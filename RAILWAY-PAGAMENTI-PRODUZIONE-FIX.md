# Fix Railway: successo falso Stripe/PayPal senza pagamento, ordine ed email

## Problema risolto

In locale il checkout funzionava, mentre in produzione Railway poteva comparire una pagina/messaggio di successo anche se il flusso corretto del gateway non aveva completato:

- niente pagamento reale confermato;
- niente ordine creato nel database;
- niente email di conferma;
- problema presente sia su Stripe sia su PayPal.

La causa tipica in produzione è una combinazione di:

1. URL di ritorno configurate male su Railway, per esempio verso `/checkout/success` invece dei controller gateway;
2. snapshot checkout non salvato nel database prima del redirect al gateway;
3. fallback troppo permissivo, che poteva continuare anche senza dati checkout completi.

## File modificati

- `src/main/java/com/delta/store/web/payment/service/CheckoutSnapshotService.java`
- `src/main/java/com/delta/store/web/payment/processor/StripePaymentProcessor.java`
- `src/main/java/com/delta/store/web/payment/service/PayPalApiService.java`
- `src/main/java/com/delta/store/web/shop/StripeWebhookController.java`
- `src/main/resources/application.properties`
- `src/main/resources/application-prod.properties`

## Cosa cambia

### 1. Snapshot checkout obbligatorio

Prima il salvataggio snapshot ignorava eventuali errori. Ora, se lo snapshot non viene salvato, il cliente non viene mandato a Stripe/PayPal.

Questo evita il caso peggiore: cliente reindirizzato al pagamento ma al ritorno l'app non ha più carrello, dati spedizione e importi per creare l'ordine.

### 2. Stripe success URL protetta

La URL Stripe deve essere:

```text
/checkout/stripe/success?session_id={CHECKOUT_SESSION_ID}
```

Se su Railway `APP_PAYMENT_SUCCESS_URL` è impostata male, per esempio verso `/checkout/success`, il codice la corregge automaticamente usando `APP_BASE_URL`.

### 3. PayPal return URL protetta

La return PayPal deve essere:

```text
/paypal/success
```

Se su Railway `PAYPAL_RETURN_URL` è impostata male, il codice la corregge automaticamente usando `APP_BASE_URL`.

### 4. Webhook Stripe più sicuro

Il webhook Stripe non crea più un ordine vuoto se manca lo snapshot checkout. In quel caso restituisce errore 500, così Stripe può ritentare e nei log Railway si vede chiaramente la causa.

## Variabili Railway consigliate

Impostare nel servizio Spring Boot su Railway:

```env
SPRING_PROFILES_ACTIVE=prod
APP_BASE_URL=https://tuo-dominio.up.railway.app

DB_URL=jdbc:postgresql://HOST:PORT/DATABASE
DB_USERNAME=USERNAME
DB_PASSWORD=PASSWORD

STRIPE_SECRET_KEY=sk_live_o_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

PAYPAL_API_URL=https://api-m.paypal.com
PAYPAL_CLIENT_ID=...
PAYPAL_CLIENT_SECRET=...
PAYPAL_CURRENCY=EUR

BREVO_SMTP_LOGIN=...
BREVO_SMTP_KEY=...
MAIL_FROM=email-verificata-su-brevo@dominio.it
MAIL_FROM_NAME=onlinestore
```

Queste possono essere omesse se `APP_BASE_URL` è corretto, perché l'app le costruisce da sola:

```env
APP_PAYMENT_SUCCESS_URL=https://tuo-dominio.up.railway.app/checkout/stripe/success?session_id={CHECKOUT_SESSION_ID}
APP_PAYMENT_CANCEL_URL=https://tuo-dominio.up.railway.app/checkout/stripe/cancel
PAYPAL_RETURN_URL=https://tuo-dominio.up.railway.app/paypal/success
PAYPAL_CANCEL_URL=https://tuo-dominio.up.railway.app/paypal/cancel
```

## Da controllare su Stripe

Nel pannello Stripe, il webhook deve puntare a:

```text
https://tuo-dominio.up.railway.app/stripe/webhook
```

Evento necessario:

```text
checkout.session.completed
```

Il fallback `/checkout/stripe/success` crea comunque l'ordine se il pagamento risulta `paid`, ma il webhook resta consigliato.

## Da controllare su PayPal

Usare credenziali coerenti:

- sandbox con `https://api-m.sandbox.paypal.com`;
- live con `https://api-m.paypal.com`.

Non mischiare client ID sandbox con API live o viceversa.
