package com.delta.store.web.payment.service;

import com.delta.store.order.model.OrderTempData;
import com.delta.store.web.payment.model.CheckoutSnapshot;
import com.delta.store.web.payment.repository.CheckoutSnapshotRepository;

import jakarta.transaction.Transactional;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CheckoutSnapshotService {

    private final CheckoutSnapshotRepository repo;
    private final ObjectMapper objectMapper;

    public CheckoutSnapshotService(CheckoutSnapshotRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    // =========================================================
    // STRIPE (compatibilità con codice esistente)
    // =========================================================

    /**
     * Salva snapshot legato a stripeSessionId.
     * (Metodo usato da StripePaymentProcessor)
     */
    @Transactional
    public void save(String stripeSessionId, OrderTempData data) {
        saveStripe(stripeSessionId, data);
    }

    @Transactional
    public void saveStripe(String stripeSessionId, OrderTempData data) {
        if (stripeSessionId == null || stripeSessionId.isBlank() || data == null) return;
        try {
            CheckoutSnapshot s = new CheckoutSnapshot();
            s.setStripeSessionId(stripeSessionId);
            s.setPayloadJson(objectMapper.writeValueAsString(data));
            s.setCreatedAt(LocalDateTime.now());
            s.setExpiresAt(LocalDateTime.now().plusHours(2));
            repo.save(s);
        } catch (Exception ignored) {
            // non blocchiamo il redirect a Stripe
        }
    }

    public Optional<OrderTempData> load(String stripeSessionId) {
        return loadStripe(stripeSessionId);
    }

    public Optional<OrderTempData> loadStripe(String stripeSessionId) {
        try {
            if (stripeSessionId == null || stripeSessionId.isBlank()) return Optional.empty();

            Optional<CheckoutSnapshot> opt = repo.findByStripeSessionId(stripeSessionId);
            if (opt.isEmpty()) return Optional.empty();

            CheckoutSnapshot s = opt.get();
            if (s.getExpiresAt() != null && s.getExpiresAt().isBefore(LocalDateTime.now())) {
                repo.deleteByStripeSessionId(stripeSessionId);
                return Optional.empty();
            }

            OrderTempData data = objectMapper.readValue(s.getPayloadJson(), OrderTempData.class);
            return Optional.ofNullable(data);

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Transactional
    public void delete(String stripeSessionId) {
        deleteStripe(stripeSessionId);
    }

    @Transactional
    public void deleteStripe(String stripeSessionId) {
        if (stripeSessionId == null || stripeSessionId.isBlank()) return;
        repo.deleteByStripeSessionId(stripeSessionId);
    }

    // =========================================================
    // PAYPAL
    // =========================================================

    @Transactional
    public void savePayPal(String paypalOrderId, OrderTempData data) {
        if (paypalOrderId == null || paypalOrderId.isBlank() || data == null) return;
        try {
            CheckoutSnapshot s = new CheckoutSnapshot();
            s.setPaypalOrderId(paypalOrderId);
            s.setPayloadJson(objectMapper.writeValueAsString(data));
            s.setCreatedAt(LocalDateTime.now());
            s.setExpiresAt(LocalDateTime.now().plusHours(2));
            repo.save(s);
        } catch (Exception ignored) {
            // non blocchiamo il redirect a PayPal
        }
    }

    public Optional<OrderTempData> loadPayPal(String paypalOrderId) {
        try {
            if (paypalOrderId == null || paypalOrderId.isBlank()) return Optional.empty();

            Optional<CheckoutSnapshot> opt = repo.findByPaypalOrderId(paypalOrderId);
            if (opt.isEmpty()) return Optional.empty();

            CheckoutSnapshot s = opt.get();
            if (s.getExpiresAt() != null && s.getExpiresAt().isBefore(LocalDateTime.now())) {
                repo.deleteByPaypalOrderId(paypalOrderId);
                return Optional.empty();
            }

            OrderTempData data = objectMapper.readValue(s.getPayloadJson(), OrderTempData.class);
            return Optional.ofNullable(data);

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Transactional
    public void deletePayPal(String paypalOrderId) {
        if (paypalOrderId == null || paypalOrderId.isBlank()) return;
        repo.deleteByPaypalOrderId(paypalOrderId);
    }
}
