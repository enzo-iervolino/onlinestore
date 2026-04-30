package com.delta.store.web.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
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
import java.util.*;

/**
 * Client PayPal REST (Orders v2) via chiamate HTTP.
 *
 * Flow standard (intent=CAPTURE):
 * 1) /v1/oauth2/token
 * 2) /v2/checkout/orders (create)
 * 3) redirect buyer al link "approve"
 * 4) /v2/checkout/orders/{id}/capture (dopo il return_url)
 */
@Service
public class PayPalApiService {

    private final RestTemplate restTemplate;

    @Value("${paypal.api.url:https://api-m.sandbox.paypal.com}")
    private String apiBaseUrl;

    @Value("${paypal.client.id:}")
    private String clientId;

    @Value("${paypal.client.secret:}")
    private String clientSecret;

    @Value("${paypal.return.url:http://localhost:8081/paypal/success}")
    private String returnUrl;

    @Value("${paypal.cancel.url:http://localhost:8081/paypal/cancel}")
    private String cancelUrl;

    @Value("${paypal.currency:EUR}")
    private String currency;

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiresAt;

    public PayPalApiService() {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        rf.setReadTimeout((int) Duration.ofSeconds(25).toMillis());
        this.restTemplate = new RestTemplate(rf);
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && apiBaseUrl != null && !apiBaseUrl.isBlank();
    }

    public record CreateOrderResult(String orderId, String approveUrl) {}

    public record OrderInfo(String status,
                            BigDecimal capturedAmount,
                            String capturedCurrency,
                            String payerEmail) {}

    /**
     * Crea un PayPal Order e ritorna (orderId + approveUrl).
     */
    public CreateOrderResult createOrder(BigDecimal amount) {

        if (!isConfigured()) {
            throw new IllegalStateException("PayPal non configurato");
        }

        BigDecimal amt = safe2(amount);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Importo PayPal non valido");
        }

        String token = getAccessToken();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intent", "CAPTURE");

        Map<String, Object> amountObj = new LinkedHashMap<>();
        amountObj.put("currency_code", currency);
        amountObj.put("value", amt.setScale(2, RoundingMode.HALF_UP).toPlainString());

        Map<String, Object> purchaseUnit = new LinkedHashMap<>();
        purchaseUnit.put("amount", amountObj);
        body.put("purchase_units", List.of(purchaseUnit));

        Map<String, Object> appCtx = new LinkedHashMap<>();
        appCtx.put("return_url", returnUrl);
        appCtx.put("cancel_url", cancelUrl);
        appCtx.put("user_action", "PAY_NOW");
        appCtx.put("shipping_preference", "NO_SHIPPING");
        body.put("application_context", appCtx);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = join(apiBaseUrl, "/v2/checkout/orders");

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

            Map<String, Object> map = resp.getBody();
            if (map == null) {
                throw new IllegalStateException("Risposta PayPal vuota");
            }

            String orderId = asString(map.get("id"));
            String approveUrl = extractApproveUrl(map);

            if (orderId == null || orderId.isBlank() || approveUrl == null || approveUrl.isBlank()) {
                throw new IllegalStateException("PayPal create order: id/link mancanti");
            }

            return new CreateOrderResult(orderId, approveUrl);

        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("PayPal create order KO: " + shortBody(e), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("PayPal create order KO: " + e.getMessage(), e);
        }
    }

    /**
     * Esegue la capture del PayPal Order. Se l’ordine è già stato catturato,
     * prova a leggere i dettagli e restituisce comunque lo stato.
     */
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

        HttpEntity<String> req = new HttpEntity<>("{}", headers);

        String url = join(apiBaseUrl, "/v2/checkout/orders/" + orderId + "/capture");

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, req, Map.class);
            Map<String, Object> body = resp.getBody();
            return toOrderInfo(body);

        } catch (HttpClientErrorException.UnprocessableEntity e) {
            // tipico: ORDER_ALREADY_CAPTURED
            OrderInfo info = getOrder(orderId);
            if (info != null) return info;
            throw new IllegalStateException("PayPal capture KO: " + shortBody(e), e);

        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("PayPal capture KO: " + shortBody(e), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("PayPal capture KO: " + e.getMessage(), e);
        }
    }

    /**
     * Legge i dettagli dell’order (utile se capture è già avvenuta).
     */
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

        HttpEntity<Void> req = new HttpEntity<>(headers);
        String url = join(apiBaseUrl, "/v2/checkout/orders/" + orderId);

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, req, Map.class);
            return toOrderInfo(resp.getBody());
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================
    // TOKEN
    // =========================================================

    private String getAccessToken() {

        if (cachedToken != null && cachedTokenExpiresAt != null) {
            // anticipo di 60s
            if (Instant.now().isBefore(cachedTokenExpiresAt.minusSeconds(60))) {
                return cachedToken;
            }
        }

        String url = join(apiBaseUrl, "/v1/oauth2/token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret)
                .getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + basic);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, req, Map.class);
            Map<String, Object> map = resp.getBody();

            if (map == null) {
                throw new IllegalStateException("Token PayPal: risposta vuota");
            }

            String token = asString(map.get("access_token"));
            Number expiresIn = asNumber(map.get("expires_in"));

            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Token PayPal: access_token mancante");
            }

            long sec = expiresIn != null ? expiresIn.longValue() : 300;
            cachedToken = token;
            cachedTokenExpiresAt = Instant.now().plusSeconds(Math.max(60, sec));

            return token;

        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("Token PayPal KO: " + shortBody(e), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Token PayPal KO: " + e.getMessage(), e);
        }
    }

    // =========================================================
    // PARSING HELPERS
    // =========================================================

    private OrderInfo toOrderInfo(Map<String, Object> map) {
        if (map == null) return new OrderInfo(null, null, null, null);

        String status = asString(map.get("status"));

        // payer email
        String payerEmail = null;
        Object payerObj = map.get("payer");
        if (payerObj instanceof Map<?, ?> payer) {
            payerEmail = asString(payer.get("email_address"));
        }

        BigDecimal capturedAmount = null;
        String capturedCurrency = null;

        // purchase_units[].payments.captures[].amount.value
        Object puObj = map.get("purchase_units");
        if (puObj instanceof List<?> puList && !puList.isEmpty()) {
            Object firstPu = puList.get(0);
            if (firstPu instanceof Map<?, ?> pu) {
                Object paymentsObj = pu.get("payments");
                if (paymentsObj instanceof Map<?, ?> payments) {
                    Object capturesObj = payments.get("captures");
                    if (capturesObj instanceof List<?> captures && !captures.isEmpty()) {
                        Object firstCap = captures.get(0);
                        if (firstCap instanceof Map<?, ?> cap) {
                            Object amountObj = cap.get("amount");
                            if (amountObj instanceof Map<?, ?> amt) {
                                capturedCurrency = asString(amt.get("currency_code"));
                                String v = asString(amt.get("value"));
                                if (v != null && !v.isBlank()) {
                                    try {
                                        capturedAmount = new BigDecimal(v).setScale(2, RoundingMode.HALF_UP);
                                    } catch (Exception ignored) {
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

    private String extractApproveUrl(Map<String, Object> map) {
        Object linksObj = map.get("links");
        if (!(linksObj instanceof List<?> links)) return null;

        for (Object o : links) {
            if (!(o instanceof Map<?, ?> link)) continue;

            String rel = asString(link.get("rel"));
            String href = asString(link.get("href"));

            if (href == null || href.isBlank()) continue;

            if ("approve".equalsIgnoreCase(rel) || "payer-action".equalsIgnoreCase(rel)) {
                return href;
            }
        }
        return null;
    }

    private String shortBody(HttpClientErrorException e) {
        try {
            String b = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            if (b == null) return e.getStatusCode().toString();
            b = b.trim();
            if (b.length() > 220) b = b.substring(0, 220) + "...";
            return e.getStatusCode() + " " + b;
        } catch (Exception ex) {
            return e.getStatusCode().toString();
        }
    }

    private String join(String base, String path) {
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!path.startsWith("/")) path = "/" + path;
        return base + path;
    }

    private BigDecimal safe2(BigDecimal v) {
        if (v == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Number asNumber(Object o) {
        if (o instanceof Number n) return n;
        if (o == null) return null;
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }
}
