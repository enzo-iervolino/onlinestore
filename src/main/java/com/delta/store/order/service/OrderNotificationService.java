package com.delta.store.order.service;
import com.delta.store.order.model.*;
import com.delta.store.order.repository.OrderMailJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
/** The job commits with the order; SMTP is performed later, outside checkout. */
@Service
public class OrderNotificationService {
    private final OrderMailJobRepository jobs;
    public OrderNotificationService(OrderMailJobRepository jobs){this.jobs=jobs;}
    @Transactional
    public void sendOrderConfirmationAfterCommit(Order order) {
        if(order==null || order.getId()==null)return;
        if(!jobs.existsById(order.getId())) { OrderMailJob j=new OrderMailJob();j.orderId=order.getId();j.nextAttempt=LocalDateTime.now();jobs.save(j); }
    }
}
