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

## Stripe

```env
APP_PAYMENT_SUCCESS_URL=${APP_BASE_URL}/checkout/stripe/success?session_id={CHECKOUT_SESSION_ID}
APP_PAYMENT_CANCEL_URL=${APP_BASE_URL}/checkout/stripe/cancel
```

Nel pannello Stripe, il webhook deve puntare a:

```text
https://tuo-dominio.up.railway.app/stripe/webhook
```

## PayPal

Sandbox:

```env
PAYPAL_API_URL=https://api-m.sandbox.paypal.com
PAYPAL_CLIENT_ID=...
PAYPAL_CLIENT_SECRET=...
PAYPAL_CURRENCY=EUR
PAYPAL_RETURN_URL=${APP_BASE_URL}/paypal/success
PAYPAL_CANCEL_URL=${APP_BASE_URL}/paypal/cancel
```

Live:

```env
PAYPAL_API_URL=https://api-m.paypal.com
```

## Railway / porta

Railway imposta automaticamente `PORT`. Il progetto ora legge:

```properties
server.port=${PORT:${SERVER_PORT:8081}}
```

Non impostare manualmente una porta fissa su Railway se non necessario.

## Nota sul problema checkout

La pagina checkout ora non dipende più solo dalla chiamata AJAX `/checkout/seleziona-metodo-pagamento`: il metodo di pagamento viene inviato anche con il submit finale `/checkout/salva-spedizione`. Inoltre gli errori generali vengono mostrati in pagina, quindi eventuali variabili mancanti non risultano più come pagina apparentemente bloccata.
