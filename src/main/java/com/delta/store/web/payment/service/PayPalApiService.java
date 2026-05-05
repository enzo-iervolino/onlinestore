package com.delta.store.web.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PayPalApiService {

    private static final Logger log = LoggerFactory.getLogger(PayPalApiService.class);

    private final RestTemplate restTemplate;

    @Value("${paypal.api.url:https://api-m.sandbox.paypal.com}")
    private String apiBaseUrl;

    @Value("${paypal.client.id:}")
    private String clientId;

    @Value("${paypal.client.secret:}")
    private String clientSecret;

    @Value("${paypal.return.url:http://localhost:8081/paypal/success}")
    private String returnUrl;

    @Value("${paypal.cancel.url:http://localhost:8081/checkout?error=paypal_cancel}")
    private String cancelUrl;

    @Value("${paypal.currency:EUR}")
    private String currency;

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiresAt;

    public PayPalApiService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(25).toMillis());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && apiBaseUrl != null && !apiBaseUrl.isBlank();
    }

    public record CreateOrderResult(String orderId, String approveUrl) {
    }

    public record OrderInfo(String status, BigDecimal capturedAmount, String capturedCurrency, String payerEmail) {
    }

    public CreateOrderResult createOrder(BigDecimal amount) {
        if (!isConfigured()) {
            throw new IllegalStateException("PayPal non configurato: controlla PAYPAL_CLIENT_ID, PAYPAL_CLIENT_SECRET e PAYPAL_API_URL");
        }

        BigDecimal normalizedAmount = safe2(amount);

        if (normalizedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Importo PayPal non valido: " + normalizedAmount);
        }

        validateRedirectUrls();

        String token = getAccessToken();

        Map<String, Object> amountObject = new LinkedHashMap<>();
        amountObject.put("currency_code", normalizeCurrency(currency));
        amountObject.put("value", normalizedAmount.toPlainString());

        Map<String, Object> purchaseUnit = new LinkedHashMap<>();
        purchaseUnit.put("amount", amountObject);

        Map<String, Object> applicationContext = new LinkedHashMap<>();
        applicationContext.put("return_url", returnUrl);
        applicationContext.put("cancel_url", cancelUrl);
        applicationContext.put("user_action", "PAY_NOW");
        applicationContext.put("shipping_preference", "NO_SHIPPING");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intent", "CAPTURE");
        body.put("purchase_units", List.of(purchaseUnit));
        body.put("application_context", applicationContext);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = join(apiBaseUrl, "/v2/checkout/orders");

        log.debug("Creazione ordine PayPal - apiBaseUrl={}, amount={}, currency={}, returnUrl={}, cancelUrl={}",
                apiBaseUrl,
                normalizedAmount,
                normalizeCurrency(currency),
                returnUrl,
                cancelUrl);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            Map<?, ?> responseBody = response.getBody();

            if (responseBody == null) {
                throw new IllegalStateException("Risposta PayPal vuota durante la creazione ordine");
            }

            String orderId = asString(responseBody.get("id"));
            String approveUrl = extractApproveUrl(responseBody);

            if (orderId == null || orderId.isBlank()) {
                throw new IllegalStateException("PayPal create order: id mancante nella risposta");
            }

            if (approveUrl == null || approveUrl.isBlank()) {
                throw new IllegalStateException("PayPal create order: approveUrl mancante nella risposta");
            }

            log.debug("Ordine PayPal creato correttamente - orderId={}", orderId);

            return new CreateOrderResult(orderId, approveUrl);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("PayPal create order KO: " + shortBody(e), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("PayPal create order KO: " + e.getMessage(), e);
        }
    }

    public OrderInfo captureOrder(String orderId) {
        if (!isConfigured()) {
            throw new IllegalStateException("PayPal non configurato");
        }

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId PayPal mancante");
        }

        String token = getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<String> request = new HttpEntity<>("{}", headers);
        String url = join(apiBaseUrl, "/v2/checkout/orders/" + orderId + "/capture");

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            return toOrderInfo(response.getBody());
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            OrderInfo info = getOrder(orderId);

            if (info != null) {
                return info;
            }

            throw new IllegalStateException("PayPal capture KO: " + shortBody(e), e);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("PayPal capture KO: " + shortBody(e), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("PayPal capture KO: " + e.getMessage(), e);
        }
    }

    public OrderInfo getOrder(String orderId) {
        if (!isConfigured()) {
            throw new IllegalStateException("PayPal non configurato");
        }

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId PayPal mancante");
        }

        String token = getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);
        String url = join(apiBaseUrl, "/v2/checkout/orders/" + orderId);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            return toOrderInfo(response.getBody());
        } catch (Exception e) {
            log.warn("Impossibile leggere ordine PayPal {}: {}", orderId, e.getMessage());
            return null;
        }
    }

    private String getAccessToken() {
        if (cachedToken != null && cachedTokenExpiresAt != null
                && Instant.now().isBefore(cachedTokenExpiresAt.minusSeconds(60))) {
            return cachedToken;
        }

        String url = join(apiBaseUrl, "/v1/oauth2/token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String basic = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)
        );

        headers.set("Authorization", "Basic " + basic);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            Map<?, ?> responseBody = response.getBody();

            if (responseBody == null) {
                throw new IllegalStateException("Token PayPal: risposta vuota");
            }

            String token = asString(responseBody.get("access_token"));
            Number expiresIn = asNumber(responseBody.get("expires_in"));

            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Token PayPal: access_token mancante");
            }

            long seconds = expiresIn != null ? expiresIn.longValue() : 300;

            cachedToken = token;
            cachedTokenExpiresAt = Instant.now().plusSeconds(Math.max(60, seconds));

            return token;
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("Token PayPal KO: " + shortBody(e), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Token PayPal KO: " + e.getMessage(), e);
        }
    }

    private OrderInfo toOrderInfo(Map<?, ?> map) {
        if (map == null) {
            return new OrderInfo(null, null, null, null);
        }

        String status = asString(map.get("status"));
        String payerEmail = null;
        BigDecimal capturedAmount = null;
        String capturedCurrency = null;

        Object payerObject = map.get("payer");

        if (payerObject instanceof Map<?, ?> payer) {
            payerEmail = asString(payer.get("email_address"));
        }

        Object purchaseUnitsObject = map.get("purchase_units");

        if (purchaseUnitsObject instanceof List<?> purchaseUnits && !purchaseUnits.isEmpty()) {
            Object firstPurchaseUnitObject = purchaseUnits.get(0);

            if (firstPurchaseUnitObject instanceof Map<?, ?> firstPurchaseUnit) {
                Object paymentsObject = firstPurchaseUnit.get("payments");

                if (paymentsObject instanceof Map<?, ?> payments) {
                    Object capturesObject = payments.get("captures");

                    if (capturesObject instanceof List<?> captures && !captures.isEmpty()) {
                        Object firstCaptureObject = captures.get(0);

                        if (firstCaptureObject instanceof Map<?, ?> firstCapture) {
                            Object amountObject = firstCapture.get("amount");

                            if (amountObject instanceof Map<?, ?> amount) {
                                capturedCurrency = asString(amount.get("currency_code"));
                                String value = asString(amount.get("value"));

                                if (value != null && !value.isBlank()) {
                                    try {
                                        capturedAmount = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
                                    } catch (Exception ignored) {
                                        capturedAmount = null;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return new OrderInfo(status, capturedAmount, capturedCurrency, payerEmail);
    }

    private String extractApproveUrl(Map<?, ?> map) {
        Object linksObject = map.get("links");

        if (!(linksObject instanceof List<?> links)) {
            return null;
        }

        for (Object item : links) {
            if (!(item instanceof Map<?, ?> link)) {
                continue;
            }

            String rel = asString(link.get("rel"));
            String href = asString(link.get("href"));

            if (href == null || href.isBlank()) {
                continue;
            }

            if ("approve".equalsIgnoreCase(rel) || "payer-action".equalsIgnoreCase(rel)) {
                return href;
            }
        }

        return null;
    }

    private void validateRedirectUrls() {
        if (returnUrl == null || returnUrl.isBlank()) {
            throw new IllegalStateException("paypal.return.url mancante");
        }

        if (cancelUrl == null || cancelUrl.isBlank()) {
            throw new IllegalStateException("paypal.cancel.url mancante");
        }

        if (!startsWithHttp(returnUrl)) {
            throw new IllegalStateException("paypal.return.url non valido: deve iniziare con http:// oppure https://");
        }

        if (!startsWithHttp(cancelUrl)) {
            throw new IllegalStateException("paypal.cancel.url non valido: deve iniziare con http:// oppure https://");
        }

        if (isLocalhost(returnUrl) || isLocalhost(cancelUrl)) {
            log.warn("URL PayPal ancora su localhost. Va bene solo in locale. Online imposta APP_BASE_URL o PAYPAL_RETURN_URL/PAYPAL_CANCEL_URL.");
        }
    }

    private boolean startsWithHttp(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private boolean isLocalhost(String value) {
        return value.contains("localhost") || value.contains("127.0.0.1");
    }

    private String shortBody(HttpClientErrorException e) {
        try {
            String body = e.getResponseBodyAsString(StandardCharsets.UTF_8);

            if (body == null || body.isBlank()) {
                return e.getStatusCode().toString();
            }

            body = body.trim();

            if (body.length() > 500) {
                body = body.substring(0, 500) + "...";
            }

            return e.getStatusCode() + " " + body;
        } catch (Exception ex) {
            return e.getStatusCode().toString();
        }
    }

    private String join(String base, String path) {
        String normalizedBase = base == null ? "" : base.trim();
        String normalizedPath = path == null ? "" : path.trim();

        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }

        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }

        return normalizedBase + normalizedPath;
    }

    private BigDecimal safe2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            return "EUR";
        }

        return value.trim().toUpperCase();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Number asNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }

        if (value == null) {
            return null;
        }

        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}