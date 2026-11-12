package com.delta.store.web.payment.service;

import com.delta.store.order.model.OrderTempData;
import com.delta.store.web.payment.model.CheckoutSnapshot;
import com.delta.store.web.payment.repository.CheckoutSnapshotRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CheckoutSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutSnapshotService.class);

    private final CheckoutSnapshotRepository repo;
    private final ObjectMapper objectMapper;

    public CheckoutSnapshotService(CheckoutSnapshotRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    // =========================================================
    // STRIPE
    // =========================================================

    /**
     * Salva snapshot legato a stripeSessionId.
     * Metodo usato da StripePaymentProcessor.
     *
     * Nota produzione/Railway:
     * se questo salvataggio fallisce NON dobbiamo continuare verso Stripe,
     * perché al ritorno dal pagamento non avremmo più carrello/spedizione
     * per creare ordine, scaricare stock e inviare email.
     */
    @Transactional
    public void save(String stripeSessionId, OrderTempData data) {
        saveStripe(stripeSessionId, data);
    }

    @Transactional
    public void saveStripe(String stripeSessionId, OrderTempData data) {
        if (stripeSessionId == null || stripeSessionId.isBlank()) {
            throw new IllegalArgumentException("stripeSessionId mancante per snapshot checkout Stripe");
        }
        if (data == null) {
            throw new IllegalArgumentException("OrderTempData mancante per snapshot checkout Stripe");
        }

        try {
            CheckoutSnapshot s = repo.findByStripeSessionId(stripeSessionId).orElseGet(CheckoutSnapshot::new);
            s.setStripeSessionId(stripeSessionId);
            s.setPaypalOrderId(null);
            s.setPayloadJson(objectMapper.writeValueAsString(data));
            s.setCreatedAt(LocalDateTime.now());
            s.setExpiresAt(LocalDateTime.now().plusHours(2));
            repo.saveAndFlush(s);
            log.info("Snapshot checkout Stripe salvato - stripeSessionId={}", stripeSessionId);
        } catch (Exception e) {
            log.error("Snapshot checkout Stripe NON salvato - stripeSessionId={}. Blocco il redirect a Stripe.", stripeSessionId, e);
            throw new IllegalStateException("Impossibile preparare il checkout Stripe in produzione. Riprova o controlla il database Railway/checkoutsnapshots.", e);
        }
    }

    public Optional<OrderTempData> load(String stripeSessionId) {
        return loadStripe(stripeSessionId);
    }

    public Optional<OrderTempData> loadStripe(String stripeSessionId) {
        try {
            if (stripeSessionId == null || stripeSessionId.isBlank()) {
                return Optional.empty();
            }

            Optional<CheckoutSnapshot> opt = repo.findByStripeSessionId(stripeSessionId);
            if (opt.isEmpty()) {
                log.warn("Snapshot checkout Stripe non trovato - stripeSessionId={}", stripeSessionId);
                return Optional.empty();
            }

            CheckoutSnapshot s = opt.get();

            OrderTempData data = objectMapper.readValue(s.getPayloadJson(), OrderTempData.class);
            return Optional.ofNullable(data);
        } catch (Exception e) {
            log.error("Errore lettura snapshot checkout Stripe - stripeSessionId={}", stripeSessionId, e);
            return Optional.empty();
        }
    }

    @Transactional
    public void delete(String stripeSessionId) {
        deleteStripe(stripeSessionId);
    }

    @Transactional
    public void deleteStripe(String stripeSessionId) {
        if (stripeSessionId == null || stripeSessionId.isBlank()) {
            return;
        }
        repo.deleteByStripeSessionId(stripeSessionId);
    }

    // =========================================================
    // PAYPAL
    // =========================================================

    @Transactional
    public void savePayPal(String paypalOrderId, OrderTempData data) {
        if (paypalOrderId == null || paypalOrderId.isBlank()) {
            throw new IllegalArgumentException("paypalOrderId mancante per snapshot checkout PayPal");
        }
        if (data == null) {
            throw new IllegalArgumentException("OrderTempData mancante per snapshot checkout PayPal");
        }

        try {
            CheckoutSnapshot s = repo.findByPaypalOrderId(paypalOrderId).orElseGet(CheckoutSnapshot::new);
            s.setStripeSessionId(null);
            s.setPaypalOrderId(paypalOrderId);
            s.setPayloadJson(objectMapper.writeValueAsString(data));
            s.setCreatedAt(LocalDateTime.now());
            s.setExpiresAt(LocalDateTime.now().plusHours(2));
            repo.saveAndFlush(s);
            log.info("Snapshot checkout PayPal salvato - paypalOrderId={}", paypalOrderId);
        } catch (Exception e) {
            log.error("Snapshot checkout PayPal NON salvato - paypalOrderId={}. Blocco il redirect a PayPal.", paypalOrderId, e);
            throw new IllegalStateException("Impossibile preparare il checkout PayPal in produzione. Riprova o controlla il database Railway/checkoutsnapshots.", e);
        }
    }

    public Optional<OrderTempData> loadPayPal(String paypalOrderId) {
        try {
            if (paypalOrderId == null || paypalOrderId.isBlank()) {
                return Optional.empty();
            }

            Optional<CheckoutSnapshot> opt = repo.findByPaypalOrderId(paypalOrderId);
            if (opt.isEmpty()) {
                log.warn("Snapshot checkout PayPal non trovato - paypalOrderId={}", paypalOrderId);
                return Optional.empty();
            }

            CheckoutSnapshot s = opt.get();

            OrderTempData data = objectMapper.readValue(s.getPayloadJson(), OrderTempData.class);
            return Optional.ofNullable(data);
        } catch (Exception e) {
            log.error("Errore lettura snapshot checkout PayPal - paypalOrderId={}", paypalOrderId, e);
            return Optional.empty();
        }
    }

    @Transactional
    public void deletePayPal(String paypalOrderId) {
        if (paypalOrderId == null || paypalOrderId.isBlank()) {
            return;
        }
        repo.deleteByPaypalOrderId(paypalOrderId);
    }
}
