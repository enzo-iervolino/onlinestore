package com.delta.store.web.payment.service;

import com.delta.store.web.payment.model.CheckoutAttempt;
import com.delta.store.web.payment.repository.*;
import com.delta.store.order.model.*;
import com.delta.store.order.repository.OrderRepository;
import com.delta.store.order.service.*;
import com.delta.store.product.repository.*;
import com.delta.store.giftcard.repository.*;
import com.delta.store.giftcard.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.math.*;
import java.util.*;

@Service
public class CheckoutAttemptService {
    private final CheckoutAttemptRepository attempts;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final GiftCardRepository gifts;
    private final GiftCardUsageRepository usages;
    private final OrderService orders;
    private final OrderRepository orderRepository;
    private final OrderNotificationService notifications;
    private final CheckoutSnapshotRepository snapshots;
    private final ObjectMapper mapper;
    public CheckoutAttemptService(CheckoutAttemptRepository attempts, ProductRepository products, ProductVariantRepository variants, GiftCardRepository gifts, GiftCardUsageRepository usages, OrderService orders, OrderRepository orderRepository, OrderNotificationService notifications, ObjectMapper mapper, CheckoutSnapshotRepository snapshots) {
        this.attempts=attempts; this.products=products; this.variants=variants; this.gifts=gifts; this.usages=usages; this.orders=orders; this.orderRepository=orderRepository; this.notifications=notifications; this.mapper=mapper; this.snapshots=snapshots;
    }
    @Transactional
    public CheckoutAttempt begin(OrderTempData data, String owner) {
        var active = attempts.findByActiveOwner(owner);
        if (active.isPresent()) return active.get();
        CheckoutQuoteService.validate(data);
        CheckoutAttempt a = new CheckoutAttempt();
        a.id=UUID.randomUUID().toString(); a.ownerToken=owner; a.activeOwner=owner;
        a.provider=data.getPaymentMethod(); a.state="PREPARED"; a.createdAt=LocalDateTime.now(); a.expiresAt=a.createdAt.plusMinutes(35); a.gatewayExpiresAt=java.time.Instant.now().plusSeconds(2100).getEpochSecond();
        data.setCheckoutAttemptId(a.id);
        // Reserve stock in stable order, with locks. On any failure the whole reservation rolls back.
        reserveStock(data, false);
        BigDecimal amount = CheckoutQuoteService.money(data.getGiftCardAmount());
        if (amount.signum() > 0) {
            GiftCard g = gifts.lockByCode(data.getGiftCardCode()).orElseThrow(() -> new IllegalArgumentException("Gift card non disponibile"));
            if (!g.isActive() || (g.getExpirationDate()!=null && g.getExpirationDate().isBefore(LocalDate.now())) || g.getRemainingAmount().compareTo(amount)<0) throw new IllegalArgumentException("Credito gift card non disponibile");
            g.setRemainingAmount(g.getRemainingAmount().subtract(amount));
        }
        data.setStockReserved(true); a.reserved=true; a.payload=mapper.writeValueAsString(data);
        return attempts.saveAndFlush(a);
    }
    public OrderTempData data(CheckoutAttempt a) { return mapper.readValue(a.payload, OrderTempData.class); }
    @Transactional(readOnly=true)
    public Optional<CheckoutAttempt> find(String id) { return id==null?Optional.empty():attempts.findById(id); }
    @Transactional(readOnly=true)
    public Optional<CheckoutAttempt> active(String owner) { return attempts.findByActiveOwner(owner); }
    @Transactional(readOnly=true)
    public Optional<CheckoutAttempt> byReference(String reference) { return attempts.findByProviderReference(reference); }
    @Transactional(readOnly=true)
    public List<CheckoutAttempt> pending() { return attempts.pending(org.springframework.data.domain.PageRequest.of(0,100)); }
    @Transactional
    public void checked(String id) { lock(id).lastCheckedAt=LocalDateTime.now(); }
    @Transactional
    public void bind(String id, String reference, String redirect) {
        CheckoutAttempt a = lock(id);
        if (a.providerReference != null && !a.providerReference.equals(reference)) throw new IllegalStateException("Tentativo già associato");
        if (!List.of("PREPARED","PENDING").contains(a.state)) throw new IllegalStateException("Tentativo non più attivo");
        a.providerReference=reference; a.redirectUrl=redirect; a.state="PENDING";
    }
    @Transactional
    public void markReview(String id) {
        CheckoutAttempt a=lock(id);
        if (!"COMPLETED".equals(a.state) && !"CANCELLED".equals(a.state)) { a.state="PAID_REVIEW"; a.recoveryNote="Pagamento da riconciliare: controllare provider e log prima di rimborso o rilascio"; }
    }
    @Transactional
    public void capturing(String id) {
        CheckoutAttempt a=lock(id);
        if (!List.of("PENDING","CAPTURING").contains(a.state)) throw new IllegalStateException("Tentativo non acquisibile");
        a.state="CAPTURING";
    }
    @Transactional
    public void cancelVerifiedUnpaid(String id) {
        CheckoutAttempt a=lock(id);
        if (!List.of("PREPARED","PENDING").contains(a.state)) return;
        if (a.reserved) {
            OrderTempData d=data(a); reserveStock(d,true);
            if (CheckoutQuoteService.money(d.getGiftCardAmount()).signum()>0) {
                GiftCard g=gifts.lockByCode(d.getGiftCardCode()).orElseThrow();
                g.setRemainingAmount(g.getRemainingAmount().add(d.getGiftCardAmount()));
                g.setUsed(false);
            }
        }
        a.reserved=false; a.state="CANCELLED"; a.activeOwner=null;
    }
    @Transactional
    public Order settle(String id, String provider, String reference, BigDecimal paid, String currency) {
        CheckoutAttempt a=lock(id);
        if (!a.provider.equals(provider) || (a.providerReference!=null && !a.providerReference.equals(reference))) throw new IllegalArgumentException("Pagamento non associato al checkout");
        if ("COMPLETED".equals(a.state)) return orderRepository.findByIdWithItems(a.orderId).orElseThrow();
        if (!a.reserved || "CANCELLED".equals(a.state)) throw new IllegalStateException("Prenotazione non disponibile: pagamento da verificare");
        OrderTempData d=data(a);
        CheckoutQuoteService.validate(d);
        if (!"EUR".equalsIgnoreCase(currency) || paid==null || CheckoutQuoteService.money(paid).compareTo(CheckoutQuoteService.money(d.getTotalAmount()))!=0) throw new IllegalArgumentException("Importo o valuta non corrispondenti");
        d.setSessionId(reference); d.setStockReserved(true);
        Order o = switch(provider) {
            case "STRIPE" -> orders.createOrderPaidByStripe(d,reference);
            case "PAYPAL" -> orders.createOrderPaidByPayPal(d,reference);
            case "GIFTCARD" -> orders.createOrderPaidByGiftcard(d);
            default -> throw new IllegalArgumentException("Metodo non supportato");
        };
        if ("GIFTCARD".equals(provider) && CheckoutQuoteService.money(d.getGiftCardAmount()).signum()==0) o.setStatus("ORDINE A TOTALE ZERO");
        if (CheckoutQuoteService.money(d.getGiftCardAmount()).signum()>0) {
            GiftCard g=gifts.lockByCode(d.getGiftCardCode()).orElseThrow();
            GiftCardUsage usage=new GiftCardUsage(); usage.setGiftCard(g); usage.setOrderId(o.getId()); usage.setAmountUsed(d.getGiftCardAmount()); usages.save(usage);
            if (g.getRemainingAmount().signum()==0) g.setUsed(true);
        }
        a.orderId=o.getId(); a.state="COMPLETED"; a.activeOwner=null; a.reserved=false; a.recoveryNote=null;
        notifications.sendOrderConfirmationAfterCommit(o);
        return o;
    }
    /** Existing pre-update payments use their own locked DB snapshot, never another cart. */
    @Transactional
    public Order settleLegacy(String provider,String reference,BigDecimal paid,String currency) {
        var snapshot=("STRIPE".equals(provider)?snapshots.lockStripe(reference):snapshots.lockPayPal(reference)).orElseThrow(()->new IllegalStateException("Snapshot pagamento non disponibile: assistenza necessaria"));
        var existing="STRIPE".equals(provider)?orders.findByStripeSessionId(reference):orders.findBySessionId(reference);
        if(existing.isPresent()) return existing.get();
        OrderTempData d=mapper.readValue(snapshot.getPayloadJson(),OrderTempData.class);
        CheckoutQuoteService.validate(d);
        if (!provider.equals(d.getPaymentMethod()) || !"EUR".equalsIgnoreCase(currency) || paid==null || paid.compareTo(d.getTotalAmount())!=0) throw new IllegalArgumentException("Pagamento non corrispondente al riepilogo");
        d.setSessionId(reference); d.setStockReserved(false);
        GiftCard g=null;
        if (CheckoutQuoteService.money(d.getGiftCardAmount()).signum()>0) {
            g=gifts.lockByCode(d.getGiftCardCode()).orElseThrow();
            if (!g.isActive() || (g.getExpirationDate()!=null && g.getExpirationDate().isBefore(LocalDate.now())) || g.getRemainingAmount().compareTo(d.getGiftCardAmount())<0) throw new IllegalStateException("Credito gift card da riconciliare");
            g.setRemainingAmount(g.getRemainingAmount().subtract(d.getGiftCardAmount()));
        }
        Order o="STRIPE".equals(provider)?orders.createOrderPaidByStripe(d,reference):orders.createOrderPaidByPayPal(d,reference);
        if(g!=null) {
            GiftCardUsage u=new GiftCardUsage(); u.setGiftCard(g); u.setOrderId(o.getId()); u.setAmountUsed(d.getGiftCardAmount()); usages.save(u); if(g.getRemainingAmount().signum()==0)g.setUsed(true);
        }
        notifications.sendOrderConfirmationAfterCommit(o);
        return o;
    }
    @Transactional(readOnly=true)
    public OrderTempData expected(String provider,String reference) {
        var a=attempts.findByProviderReference(reference);
        OrderTempData d;
        if(a.isPresent()) { if(!provider.equals(a.get().provider))throw new IllegalArgumentException("Provider errato"); d=data(a.get()); }
        else {
            var snapshot=("STRIPE".equals(provider)?snapshots.findByStripeSessionId(reference):snapshots.findByPaypalOrderId(reference)).orElseThrow(()->new IllegalStateException("Riepilogo mancante: nessuna capture autorizzata"));
            d=mapper.readValue(snapshot.getPayloadJson(),OrderTempData.class);
        }
        CheckoutQuoteService.validate(d);
        if(!provider.equals(d.getPaymentMethod()))throw new IllegalArgumentException("Pagamento non corrispondente");
        return d;
    }
    @Transactional(readOnly=true)
    public boolean hasLegacySnapshot(String provider,String reference) {
        return "STRIPE".equals(provider)?snapshots.findByStripeSessionId(reference).isPresent():snapshots.findByPaypalOrderId(reference).isPresent();
    }
    @Transactional(readOnly=true)
    public boolean ownsOrder(Long orderId,String owner,Long userId) {
        if (owner!=null && attempts.findByOrderId(orderId).map(a->owner.equals(a.ownerToken)).orElse(false)) return true;
        return userId!=null && orderRepository.findById(orderId).map(o->o.getUser()!=null && userId.equals(o.getUser().getId())).orElse(false);
    }
    private CheckoutAttempt lock(String id) { return attempts.lock(id).orElseThrow(() -> new IllegalArgumentException("Tentativo checkout non trovato")); }
    private void reserveStock(OrderTempData d, boolean release) {
        var items=new ArrayList<>(d.getCartItems());
        items.sort(Comparator.comparing((CartItemDTO i)->i.getVariantId()!=null?"V":"P").thenComparing(i->i.getVariantId()!=null?i.getVariantId():i.getProductId()));
        for (var i:items) {
            if (i.getVariantId()!=null) {
                var v=variants.findByIdForUpdate(i.getVariantId()).orElseThrow(()->new IllegalArgumentException("Variante non disponibile"));
                if (!release && (!v.isEnabled() || !v.getProduct().getId().equals(i.getProductId()))) throw new IllegalArgumentException("Variante non disponibile");
                if (!release && (v.getPrice()!=null?v.getPrice():v.getProduct().getPrice()).compareTo(i.getUnitPrice())!=0) throw new IllegalArgumentException("Prezzo cambiato: aggiorna il riepilogo");
                int qty=v.getStockQty()==null?0:v.getStockQty();
                if (!release && qty<i.getQuantity()) throw new IllegalArgumentException("Disponibilità insufficiente per " + i.getProductName());
                v.setStockQty(release?Math.addExact(qty,i.getQuantity()):qty-i.getQuantity());
            } else {
                var p=products.findByIdForUpdate(i.getProductId()).orElseThrow(()->new IllegalArgumentException("Prodotto non disponibile"));
                if (!release && !p.getVariants().isEmpty()) throw new IllegalArgumentException("Seleziona la variante");
                if (!release && p.getPrice().compareTo(i.getUnitPrice())!=0) throw new IllegalArgumentException("Prezzo cambiato: aggiorna il riepilogo");
                int qty=p.getStockQty()==null?0:p.getStockQty();
                if (!release && qty<i.getQuantity()) throw new IllegalArgumentException("Disponibilità insufficiente per " + i.getProductName());
                p.setStockQty(release?Math.addExact(qty,i.getQuantity()):qty-i.getQuantity());
            }
        }
    }
}
