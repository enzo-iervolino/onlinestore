# Invio email Brevo tramite API HTTPS in produzione Railway

## Perché questa modifica

Railway può bloccare o rendere instabili le connessioni SMTP in uscita verso:

- `smtp-relay.brevo.com:587`
- `smtp-relay.brevo.com:2525`

Nei log infatti compariva:

```text
Couldn't connect to host, port: smtp-relay.brevo.com, 2525
java.net.SocketTimeoutException: Connect timed out
```

Per evitare il blocco SMTP, in produzione l'app invia le email tramite API HTTPS Brevo:

```text
https://api.brevo.com/v3/smtp/email
```

Questa chiamata usa HTTPS porta 443, quindi è adatta a Railway.

## File modificati

- `src/main/java/com/delta/store/email/BrevoApiEmailClient.java`
- `src/main/java/com/delta/store/email/EmailServiceImpl.java`
- `src/main/java/com/delta/store/order/service/OrderEmailServiceImpl.java`
- `src/main/resources/application.properties`
- `src/main/resources/application-prod.properties`

## Variabili Railway obbligatorie

Aggiungi su Railway:

```env
MAIL_DELIVERY_MODE=brevo-api
BREVO_API_ENABLED=true
BREVO_API_KEY=la-tua-api-key-brevo
MAIL_FROM=deltastore653@gmail.com
MAIL_FROM_NAME=onlinestore
```

`MAIL_FROM` deve essere un mittente verificato in Brevo.

## Variabili SMTP non più necessarie in produzione

Puoi lasciare le variabili SMTP, ma non vengono usate quando:

```env
MAIL_DELIVERY_MODE=brevo-api
```

Quindi queste non sono più necessarie per Railway:

```env
BREVO_SMTP_LOGIN
BREVO_SMTP_KEY
BREVO_SMTP_PORT
BREVO_SMTP_HOST
MAIL_HOST
MAIL_PORT
MAIL_USERNAME
MAIL_PASSWORD
```

## Dove creare la API Key Brevo

In Brevo:

```text
Settings → SMTP & API → API Keys → Generate a new API key
```

La chiave deve essere una API key, non una SMTP key.

## Test dopo deploy

1. Fai redeploy su Railway.
2. Fai un pagamento test Stripe o PayPal.
3. Controlla i log Railway.
4. Devi vedere una riga simile:

```text
Email inviata tramite Brevo API HTTPS a cliente@email.it
Email conferma ordine #... inviata a cliente@email.it
```

5. In Brevo controlla:

```text
Transactional → Logs
```

Deve comparire un evento.


## Fix avvio applicazione

La classe `BrevoApiEmailClient` ora crea internamente il client HTTP con `RestClient.builder().build()` e non richiede più l'autowiring di `RestClient.Builder`.
Questo evita l'errore di avvio:

`No qualifying bean of type 'org.springframework.web.client.RestClient$Builder' available`

