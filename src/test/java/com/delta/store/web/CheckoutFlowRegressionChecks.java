package com.delta.store.web;

import com.delta.store.cart.model.*;
import com.delta.store.order.model.*;
import com.delta.store.order.repository.*;
import com.delta.store.order.service.*;
import com.delta.store.product.model.*;
import com.delta.store.product.repository.*;
import com.delta.store.giftcard.model.*;
import com.delta.store.giftcard.repository.*;
import com.delta.store.web.payment.*;
import com.delta.store.web.payment.model.*;
import com.delta.store.web.payment.repository.*;
import com.delta.store.web.payment.service.*;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.lang.reflect.*;
import java.util.*;

/** No provider/network/DB calls. Real services; repositories replaced by small in-memory doubles.
 * These checks do not prove database isolation; that requires the documented PostgreSQL tests. */
public class CheckoutFlowRegressionChecks {
    private static int checks;
    private static BigDecimal bd(String s){return new BigDecimal(s);}
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
    private static void rejects(Runnable action,String name){try{action.run();}catch(IllegalArgumentException|IllegalStateException|ArithmeticException e){check(true,name);return;}throw new AssertionError(name);}
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,java.util.function.BiFunction<String,Object[],Object> f){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{if(m.getName().equals("toString"))return "test-double";return f.apply(m.getName(),a==null?new Object[0]:a);});}
    static OrderTempData data(){
        OrderTempData d=new OrderTempData();d.setCustomerName("Mario Rossi");d.setCustomerEmail("mario@example.it");d.setCustomerPhone("3331234567");d.setShippingAddress1("Via Roma 1");d.setShippingZip("80047");d.setShippingCity("San Giuseppe Vesuviano");d.setShippingProvince("NA");d.setShippingCountry("IT");
        d.setSubtotal(bd("100.00"));d.setDiscountValue(bd("10.00"));d.setShippingCost(bd("5.00"));d.setGiftCardAmount(bd("20.00"));d.setGiftCardCode("GIFT-TEST");d.setTotalAmount(bd("75.00"));d.setNetSubtotal(bd("95.00"));d.setPaymentMethod("STRIPE");
        d.setCartItems(new ArrayList<>(List.of(new CartItemDTO(1L,"Prodotto","P1",1,bd("100.00")))));return d;
    }
    public static void main(String[] args){
        OrderTempData d=data();CheckoutQuoteService.validate(d);check(true,"valid mixed payment 95 = 20 gift + 75 gateway");
        d.setPaymentMethod("GIFTCARD");final var partial=d;rejects(()->CheckoutQuoteService.validate(partial),"partial gift cannot mark order paid");
        d.setGiftCardAmount(bd("95.00"));d.setTotalAmount(bd("0.00"));CheckoutQuoteService.validate(d);check(true,"fully covered gift accepted");
        d=data();d.setTotalAmount(bd("74.99"));final var wrongTotal=d;rejects(()->CheckoutQuoteService.validate(wrongTotal),"cent mismatch rejected");
        d=data();d.setGiftCardAmount(bd("20.001"));final var fractions=d;rejects(()->CheckoutQuoteService.validate(fractions),"fractional cents rejected");
        d=data();d.setCustomerEmail("invalid");final var email=d;rejects(()->CheckoutQuoteService.validate(email),"invalid email blocked server side");
        d=data();d.setShippingZip(null);final var zip=d;rejects(()->CheckoutQuoteService.validate(zip),"missing shipping blocked server side");
        d=data();d.setCustomerName("a\nb");final var name=d;rejects(()->CheckoutQuoteService.validate(name),"control characters rejected");
        rejects(()->PaymentMethod.fromSessionValue("not-a-provider"),"unknown payment rejected instead of defaulting to Stripe");
        Product p=new Product();p.setId(1L);p.setPrice(bd("100.00"));p.setName("Prodotto");p.setCodpro("P1");p.setStockQty(5);
        Cart cart=new Cart();cart.add(p,1);String fingerprint=CheckoutQuoteService.fingerprint(cart);cart.increase(1L);check(!fingerprint.equals(CheckoutQuoteService.fingerprint(cart)),"cart revision changes when quantity changes");
        CartItem detached=cart.getItems().iterator().next();detached.setQuantity(999);check(cart.getCount()==2,"cart response cannot mutate internal items");
        cart.clear();cart.add(p,999);rejects(()->cart.increase(1L),"quantity cap prevents overflow");
        Map<String,CheckoutAttempt> rows=new LinkedHashMap<>();Map<Long,Order> orderRows=new HashMap<>();Map<Long,OrderMailJob> mail=new HashMap<>();int[] issued={0},usage={0};
        CheckoutAttemptRepository ar=proxy(CheckoutAttemptRepository.class,(m,a)->switch(m){
            case "findByActiveOwner" -> rows.values().stream().filter(x->Objects.equals(x.activeOwner,a[0])).findFirst();
            case "findByProviderReference" -> rows.values().stream().filter(x->Objects.equals(x.providerReference,a[0])).findFirst();
            case "findByOrderId" -> rows.values().stream().filter(x->Objects.equals(x.orderId,a[0])).findFirst();
            case "pending" -> rows.values().stream().filter(x->List.of("PREPARED","PENDING","CAPTURING","PAID_REVIEW").contains(x.state)).toList();
            case "findById","lock" -> Optional.ofNullable(rows.get(a[0]));
            case "saveAndFlush" -> {CheckoutAttempt x=(CheckoutAttempt)a[0];rows.put(x.id,x);yield x;}
            default -> throw new UnsupportedOperationException(m);
        });
        ProductRepository pr=proxy(ProductRepository.class,(m,a)->Optional.of(p));
        ProductVariantRepository vr=proxy(ProductVariantRepository.class,(m,a)->Optional.empty());
        GiftCard g=new GiftCard();g.setCode("GIFT-TEST");g.setActive(true);g.setRemainingAmount(bd("200.00"));
        GiftCardRepository gr=proxy(GiftCardRepository.class,(m,a)->Optional.of(g));
        GiftCardUsageRepository ur=proxy(GiftCardUsageRepository.class,(m,a)->{usage[0]++;return a[0];});
        OrderRepository or=proxy(OrderRepository.class,(m,a)->Optional.ofNullable(orderRows.get(a[0])));
        OrderMailJobRepository mr=proxy(OrderMailJobRepository.class,(m,a)->{if(m.equals("existsById"))return mail.containsKey(a[0]);if(m.equals("save")){var x=(OrderMailJob)a[0];mail.put(x.orderId,x);return x;}throw new UnsupportedOperationException(m);});
        OrderService os=proxy(OrderService.class,(m,a)->{if(m.startsWith("createOrderPaid")){issued[0]++;Order o=new Order();o.setId((long)issued[0]);o.setCustomerEmail(((OrderTempData)a[0]).getCustomerEmail());orderRows.put(o.getId(),o);check(((OrderTempData)a[0]).isStockReserved(),"settlement skips already reserved stock");return o;}if(m.equals("findBySessionId"))return rows.values().stream().filter(x->Objects.equals(x.providerReference,a[0]) && "COMPLETED".equals(x.state)).findFirst().map(x->orderRows.get(x.orderId));return Optional.empty();});
        CheckoutSnapshotRepository sr=proxy(CheckoutSnapshotRepository.class,(m,a)->Optional.empty());
        CheckoutAttemptService service=new CheckoutAttemptService(ar,pr,vr,gr,ur,os,or,new OrderNotificationService(mr),new ObjectMapper(),sr);
        CheckoutAttempt attempt=service.begin(data(),"owner-1");check(p.getStockQty()==4 && g.getRemainingAmount().compareTo(bd("180.00"))==0,"stock and gift reserved before gateway");
        check(service.begin(data(),"owner-1").id.equals(attempt.id) && p.getStockQty()==4,"second start reuses active checkout");
        service.bind(attempt.id,"cs_test_payment","https://checkout.stripe.com/test");
        rejects(()->service.settle(attempt.id,"STRIPE","different",bd("75"),"eur"),"foreign payment reference rejected");
        rejects(()->service.settle(attempt.id,"STRIPE","cs_test_payment",bd("75"),"usd"),"foreign currency rejected");
        Order o=service.settle(attempt.id,"STRIPE","cs_test_payment",bd("75"),"eur");
        check(service.settle(attempt.id,"STRIPE","cs_test_payment",bd("75"),"eur").getId().equals(o.getId()) && issued[0]==1 && usage[0]==1 && mail.size()==1,"repeated settlement creates one order, usage and notification");
        check(p.getStockQty()==4 && g.getRemainingAmount().compareTo(bd("180"))==0,"settlement does not debit reservations twice");
        service.cancelVerifiedUnpaid(attempt.id);check(p.getStockQty()==4,"completed order never released as cancelled");
        CheckoutAttempt abandoned=service.begin(data(),"owner-2");service.cancelVerifiedUnpaid(abandoned.id);service.cancelVerifiedUnpaid(abandoned.id);
        check(p.getStockQty()==4 && g.getRemainingAmount().compareTo(bd("180"))==0,"cancel releases resources exactly once");
        rejects(()->service.settle(abandoned.id,"STRIPE","bad",bd("75"),"eur"),"released attempt cannot be fulfilled");
        rejects(()->service.bind(abandoned.id,"late","url"),"late gateway response cannot expose a cancelled checkout");
        check(service.ownsOrder(o.getId(),"owner-1",null) && !service.ownsOrder(o.getId(),"owner-2",null),"guest order visibility bound to owner token");
        check(service.data(attempt).getTotalAmount().compareTo(bd("75"))==0,"durable JSON quote roundtrip preserves amount");
        Map<String,Object> attributes=new HashMap<>();
        jakarta.servlet.http.HttpSession session=proxy(jakarta.servlet.http.HttpSession.class,(m,a)->switch(m){case "getAttribute"->attributes.get(a[0]);case "setAttribute"->{attributes.put((String)a[0],a[1]);yield null;}case "removeAttribute"->{attributes.remove(a[0]);yield null;}default->null;});
        Cart current=new Cart();current.add(p,1);attributes.put(CheckoutSession.CART,current);OrderTempData prior=data();prior.setCartFingerprint(CheckoutQuoteService.fingerprint(current));current.increase(1L);
        CheckoutSession.clearIfUnchanged(session,prior);check(attributes.containsKey(CheckoutSession.CART),"new cart revision survives an old payment return");
        prior.setCartFingerprint(CheckoutQuoteService.fingerprint(current));CheckoutSession.clearIfUnchanged(session,prior);check(!attributes.containsKey(CheckoutSession.CART),"unchanged paid cart is cleared");
        int[] captured={0};boolean[] wrong={true};
        PayPalApiService pp=new PayPalApiService(){
            @Override public OrderInfo getOrder(String id){return new OrderInfo("APPROVED",null,null,null,null,wrong[0]?bd("1"):bd("75"),"EUR");}
            @Override public OrderInfo captureOrder(String id){captured[0]++;return new OrderInfo("COMPLETED",bd("75"),"EUR",null,null,bd("75"),"EUR");}
        };
        CheckoutRecoveryService recovery=new CheckoutRecoveryService(service,pp,os);
        OrderTempData ppData=data();ppData.setPaymentMethod("PAYPAL");CheckoutAttempt ppAttempt=service.begin(ppData,"owner-paypal");service.bind(ppAttempt.id,"PAYPAL-TEST-ID","https://www.paypal.com/test");
        rejects(()->recovery.paypal("PAYPAL-TEST-ID"),"PayPal mismatch rejected before capture");check(captured[0]==0,"no external capture for invalid expected amount");
        wrong[0]=false;check(recovery.paypal("PAYPAL-TEST-ID").isPresent() && captured[0]==1,"approved PayPal recovered without browser");
        check(recovery.paypal("PAYPAL-TEST-ID").isPresent() && captured[0]==1,"completed PayPal retry never captures again");
        OrderTempData giftOnly=data();giftOnly.setPaymentMethod("GIFTCARD");giftOnly.setGiftCardAmount(bd("95"));giftOnly.setTotalAmount(bd("0"));
        CheckoutAttempt interrupted=service.begin(giftOnly,"owner-gift");recovery.recover();
        check("COMPLETED".equals(rows.get(interrupted.id).state),"interrupted pure gift checkout completed by recovery worker");
        Cart equalCart=new Cart();equalCart.add(p,1);attributes.put(CheckoutSession.CART,equalCart);prior.setCartFingerprint(CheckoutQuoteService.fingerprint(equalCart));prior.setCheckoutAttemptId("old-attempt");attributes.put(CheckoutSession.ATTEMPT,"new-attempt");
        CheckoutSession.clearIfUnchanged(session,prior);check(attributes.containsKey(CheckoutSession.CART),"new attempt survives old callback even with identical cart items");
        p.setStockQty(0);rejects(()->service.begin(data(),"owner-3"),"unavailable stock rejected before payment");
        System.out.println("SUCCESS: "+checks+" regression checks");
    }
}
