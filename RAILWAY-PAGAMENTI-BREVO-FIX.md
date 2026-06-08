# Fix produzione Railway: Stripe, PayPal, ordine DB e Brevo

Questa versione corregge il caso in cui in produzione Railway il cliente vedeva una pagina di successo anche se il pagamento gateway non era stato confermato correttamente e quindi non venivano creati ordine DB/email.

## Modifiche applicate

- Stripe:
  - la success URL viene accettata solo se passa da `/checkout/stripe/success?session_id={CHECKOUT_SESSION_ID}`;
  - il controller di ritorno verifica direttamente la sessione Stripe con la secret key;
  - l'ordine viene creato solo se `payment_status=paid`;
  - le chiavi Stripe vengono ripulite da virgolette, spazi e ritorni a capo eventualmente inseriti da Railway.

- PayPal:
  - la return URL viene accettata solo se passa da `/paypal/success`;
  - la capture viene eseguita solo se PayPal conferma `APPROVED`;
  - l'ordine viene creato solo se la capture torna `COMPLETED`;
  - `PAYPAL_API_URL=//api-m.sandbox.paypal.com` viene normalizzata automaticamente in `https://api-m.sandbox.paypal.com`;
  - `PAYPAL_CLIENT_ID` e `PAYPAL_CLIENT_SECRET` vengono ripulite da virgolette/spazi/ritorni a capo.

- Brevo:
  - aggiunta configurazione JavaMailSender esplicita e robusta per Railway;
  - `BREVO_SMTP_LOGIN` e `BREVO_SMTP_KEY` vengono ripulite da virgolette e ritorni a capo;
  - resta obbligatorio usare la SMTP Key di Brevo, non la API Key REST.

- Database:
  - `application-prod.properties` accetta anche `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` di Railway.

## Variabili Railway consigliate

Non inserire virgolette nel valore su Railway.

```env
SPRING_PROFILES_ACTIVE=prod
APP_BASE_URL=https://onlinestore-production-ec69.up.railway.app

SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}

BREVO_SMTP_LOGIN=acxxxxx@smtp-brevo.com
BREVO_SMTP_KEY=NUOVA_SMTP_KEY_BREVO
MAIL_FROM=deltastore653@gmail.com
MAIL_FROM_NAME=onlinestore

PAYPAL_MODE=sandbox
PAYPAL_API_URL=https://api-m.sandbox.paypal.com
PAYPAL_CLIENT_ID=...
PAYPAL_CLIENT_SECRET=...
PAYPAL_CURRENCY=EUR
PAYPAL_RETURN_URL=https://onlinestore-production-ec69.up.railway.app/paypal/success
PAYPAL_CANCEL_URL=https://onlinestore-production-ec69.up.railway.app/paypal/cancel

STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
APP_PAYMENT_SUCCESS_URL=https://onlinestore-production-ec69.up.railway.app/checkout/stripe/success?session_id={CHECKOUT_SESSION_ID}
APP_PAYMENT_CANCEL_URL=https://onlinestore-production-ec69.up.railway.app/checkout/stripe/cancel
```

## Attenzione sicurezza

Le chiavi incollate in chat o nei log devono essere considerate esposte. Rigenera:

- Stripe secret key / webhook secret, se condivise integralmente;
- PayPal client secret;
- Brevo SMTP key;
- Google client secret, se condiviso integralmente.

## Test produzione

1. Fai push del progetto aggiornato.
2. Su Railway rimuovi virgolette e righe vuote dai valori delle variabili.
3. Redeploy.
4. Prova Stripe sandbox: deve arrivare su `/checkout/stripe/success?session_id=...`.
5. Prova PayPal sandbox: deve arrivare su `/paypal/success?token=...`.
6. Verifica in DB la tabella `orders` e i log `OrderNotificationService`.
