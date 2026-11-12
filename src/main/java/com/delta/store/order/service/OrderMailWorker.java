package com.delta.store.order.service;
import com.delta.store.order.repository.OrderMailJobRepository;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
public class OrderMailWorker {
    private final OrderMailJobRepository jobs;
    private final OrderMailDeliveryService delivery;
    public OrderMailWorker(OrderMailJobRepository jobs,OrderMailDeliveryService delivery){this.jobs=jobs;this.delivery=delivery;}
    @Scheduled(fixedDelay=30000,initialDelay=20000)
    public void run(){ for(var j:jobs.findTop20ByDeliveredFalseAndNextAttemptBeforeOrderByNextAttemptAsc(java.time.LocalDateTime.now())) { try {delivery.deliver(j.orderId);}catch(Exception ex){org.slf4j.LoggerFactory.getLogger(getClass()).error("Invio ordine {} rinviato",j.orderId,ex);} } }
}
