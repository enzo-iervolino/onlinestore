# Fix PayPal capture 422 ORDER_NOT_APPROVED

Errore risolto:

```text
PayPal capture KO: 422 UNPROCESSABLE_CONTENT / ORDER_NOT_APPROVED
Payer has not yet approved the Order for payment.
```

## Causa

La capture PayPal deve essere eseguita solo dopo che il cliente ha approvato l'ordine PayPal. Se l'app chiama `/v2/checkout/orders/{orderId}/capture` quando lo stato è ancora `CREATED`, `PAYER_ACTION_REQUIRED` o comunque non `APPROVED`, PayPal risponde con `ORDER_NOT_APPROVED`.

## Modifica applicata

Sono stati modificati solo i file reali già presenti nel progetto:

- `src/main/java/com/delta/store/web/payment/service/PayPalApiService.java`
- `src/main/java/com/delta/store/web/shop/PayPalCheckoutController.java`
- `src/main/resources/application-prod.properties`

Il nuovo flusso è:

1. l'app crea l'ordine PayPal;
2. salva lo snapshot checkout;
3. reindirizza il cliente alla URL `rel=approve` restituita da PayPal;
4. quando PayPal rientra su `/paypal/success?token=...`, il controller legge prima lo stato ordine da PayPal;
5. se lo stato non è `APPROVED` o `COMPLETED`, non tenta la capture;
6. se PayPal restituisce ancora una URL `approve`/`payer-action`, il cliente viene rimandato lì;
7. solo con stato `APPROVED` viene chiamata la capture;
8. solo con stato finale `COMPLETED` viene creato l'ordine applicativo e inviata la conferma.

## Variabili consigliate in produzione

```properties
APP_BASE_URL=https://tuo-dominio-pubblico
PAYPAL_API_URL=https://api-m.paypal.com
PAYPAL_CLIENT_ID=...
PAYPAL_CLIENT_SECRET=...
PAYPAL_CURRENCY=EUR
PAYPAL_RETURN_URL=https://tuo-dominio-pubblico/paypal/success
PAYPAL_CANCEL_URL=https://tuo-dominio-pubblico/paypal/cancel
```

`PAYPAL_RETURN_URL` e `PAYPAL_CANCEL_URL` possono anche essere omesse se `APP_BASE_URL` è configurata correttamente.
