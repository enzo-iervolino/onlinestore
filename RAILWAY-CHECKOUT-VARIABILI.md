# Configurazione Railway per checkout Online Store

Questa applicazione non deve contenere chiavi o password hardcoded nel codice. Su Railway imposta le variabili nella sezione **Variables** del servizio e poi fai redeploy.

## Variabili obbligatorie principali

```env
APP_BASE_URL=https://tuo-dominio.up.railway.app
DB_URL=jdbc:postgresql://HOST:PORT/DATABASE
DB_USERNAME=...
DB_PASSWORD=...
STRIPE_SECRET_KEY=sk_live_o_sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

> Nota: `APP_BASE_URL` deve essere assoluta e deve iniziare con `https://` quando l'app è online.

## Stripe

Puoi anche non impostare queste due variabili: se mancano, l'app le costruisce da `APP_BASE_URL`.

```env
APP_PAYMENT_SUCCESS_URL=${APP_BASE_URL}/checkout/stripe/success?session_id={CHECKOUT_SESSION_ID}
APP_PAYMENT_CANCEL_URL=${APP_BASE_URL}/checkout/stripe/cancel
```

Sono accettati anche URL completi, ad esempio:

```env
APP_PAYMENT_SUCCESS_URL=https://tuo-dominio.up.railway.app/checkout/stripe/success?session_id={CHECKOUT_SESSION_ID}
APP_PAYMENT_CANCEL_URL=https://tuo-dominio.up.railway.app/checkout/stripe/cancel
```

Nel pannello Stripe, il webhook deve puntare a:

```text
https://tuo-dominio.up.railway.app/stripe/webhook
```

### Fallback Stripe senza webhook

La rotta `/checkout/stripe/success` ora verifica direttamente la sessione Stripe quando il webhook non ha ancora creato l'ordine. Se `payment_status=paid`, crea ordine, consuma eventuale giftcard, invia email e porta alla pagina `/checkout/success?orderId=...`. Il webhook resta comunque supportato e mantiene l'idempotenza tramite `stripeSessionId`.

## PayPal

Sandbox:

```env
PAYPAL_API_URL=https://api-m.sandbox.paypal.com
PAYPAL_CLIENT_ID=...
PAYPAL_CLIENT_SECRET=...
PAYPAL_CURRENCY=EUR
PAYPAL_RETURN_URL=${APP_BASE_URL}/paypal/success
PAYPAL_CANCEL_URL=${APP_BASE_URL}/checkout?error=paypal_cancel
```

Sono accettati anche URL completi:

```env
PAYPAL_RETURN_URL=https://tuo-dominio.up.railway.app/paypal/success
PAYPAL_CANCEL_URL=https://tuo-dominio.up.railway.app/checkout?error=paypal_cancel
```

Live:

```env
PAYPAL_API_URL=https://api-m.paypal.com
```

## Email ordine

Per inviare la conferma ordine dopo pagamento Stripe/PayPal imposta anche le variabili SMTP.

Esempio Gmail con password per app:

```env
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=tuoindirizzo@gmail.com
MAIL_PASSWORD=password_app_google_senza_spazi
MAIL_FROM=tuoindirizzo@gmail.com
MAIL_SMTP_AUTH=true
MAIL_STARTTLS_ENABLE=true
MAIL_STARTTLS_REQUIRED=true
```

Se `MAIL_FROM` manca, l'app usa automaticamente `MAIL_USERNAME` come mittente.

Nei log Railway ora compaiono messaggi espliciti:

```text
Email conferma ordine #... inviata a ...
Email conferma ordine #... NON inviata a ...
```

Quindi, se PayPal crea l'ordine ma non arriva l'email, controlla prima `MAIL_USERNAME`, `MAIL_PASSWORD` e `MAIL_FROM`.

## Railway / porta

Railway imposta automaticamente `PORT`. Il progetto legge:

```properties
server.port=${PORT:${SERVER_PORT:8081}}
```

Non impostare manualmente una porta fissa su Railway se non necessario.

## Service worker

Il service worker è stato disattivato perché non deve intercettare pagine dinamiche come checkout, carrello, login, Stripe e PayPal.

La nuova versione di `pwa.js` rimuove eventuali vecchi service worker e cache già installati nei browser. Questo evita l'errore console:

```text
Failed to convert value to 'Response'
```

## Nota sul problema checkout

La pagina checkout non dipende solo dalla chiamata AJAX `/checkout/seleziona-metodo-pagamento`: il metodo di pagamento viene inviato anche con il submit finale `/checkout/salva-spedizione`. Inoltre gli errori generali vengono mostrati in pagina, quindi eventuali variabili mancanti non risultano più come pagina apparentemente bloccata.

## Nota importante email checkout

L'invio email ordine è asincrono e parte solo dopo il commit dell'ordine nel database.
Quindi un timeout SMTP non deve bloccare PayPal/Stripe e non deve annullare la creazione dell'ordine.

Variabili consigliate Railway per Gmail SMTP:

```env
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=tuoindirizzo@gmail.com
MAIL_PASSWORD=password_app_google
MAIL_FROM=tuoindirizzo@gmail.com
MAIL_SMTP_AUTH=true
MAIL_STARTTLS_ENABLE=true
MAIL_STARTTLS_REQUIRED=true
MAIL_CONNECTION_TIMEOUT=10000
MAIL_TIMEOUT=10000
MAIL_WRITE_TIMEOUT=10000
```

Se Railway continua a mostrare `Connect timed out` verso `smtp.gmail.com:587`, il problema è la connessione SMTP verso Gmail dal runtime Railway. In quel caso l'ordine resta comunque salvato, ma per inviare email conviene usare un SMTP transazionale come Brevo, SendGrid o Mailgun.
