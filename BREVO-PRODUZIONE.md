# Configurazione Brevo in produzione

Questa baseline include il profilo Spring `prod` con configurazione SMTP Brevo in `src/main/resources/application-prod.properties`.

## Variabili ambiente da impostare in produzione

```properties
SPRING_PROFILES_ACTIVE=prod
APP_BASE_URL=https://tuo-dominio-online

BREVO_SMTP_LOGIN=la-tua-login-smtp-brevo
BREVO_SMTP_KEY=la-tua-chiave-smtp-brevo
MAIL_FROM=mittente-verificato@tuodominio.it
MAIL_FROM_NAME=Online Store
```

## Note importanti

- `MAIL_FROM` deve essere un mittente verificato/autorizzato su Brevo.
- Non inserire password o chiavi SMTP nei file del progetto.
- `APP_BASE_URL` viene usato per generare link pubblici corretti, ad esempio il reset password.
- Il profilo `prod` usa di default `smtp-relay.brevo.com` sulla porta `587` con STARTTLS.

## Variabili opzionali

```properties
BREVO_SMTP_HOST=smtp-relay.brevo.com
BREVO_SMTP_PORT=587
MAIL_DEBUG=false
MAIL_TEST_CONNECTION=false
MAIL_CONNECTION_TIMEOUT=10000
MAIL_TIMEOUT=10000
MAIL_WRITE_TIMEOUT=10000
```
