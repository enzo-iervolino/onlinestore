package com.delta.store.order.service;
import com.delta.store.order.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
@Service
public class OrderMailDeliveryService {
    private final OrderMailJobRepository jobs;
    private final OrderRepository orders;
    private final ReceiptPdfService pdfs;
    private final OrderEmailService emails;
    public OrderMailDeliveryService(OrderMailJobRepository jobs,OrderRepository orders,ReceiptPdfService pdfs,OrderEmailService emails) {this.jobs=jobs;this.orders=orders;this.pdfs=pdfs;this.emails=emails;}
    @Transactional
    public void deliver(Long id) {
        var optional=jobs.lock(id);if(optional.isEmpty())return;var job=optional.get();
        if(job.delivered || job.nextAttempt.isAfter(LocalDateTime.now()))return;
        try {
            var found=orders.findByIdWithItems(id);
            if(found.isEmpty()){job.delivered=true;job.lastError="Ordine eliminato prima dell’invio";return;}
            var o=found.get();
            if(o.getCustomerEmail()==null || o.getCustomerEmail().isBlank())throw new IllegalStateException("Email mancante");
            // Load and render inside a transaction; no detached lazy entity crosses a daemon thread.
            byte[] pdf=pdfs.generateReceipt(o);
            emails.sendOrderConfirmation(o.getCustomerEmail(),o,pdf);
            job.delivered=true;job.lastError=null;
        }catch(Exception ex){
            job.attempts++;job.nextAttempt=LocalDateTime.now().plusMinutes(Math.min(360,1L<<Math.min(job.attempts,8)));
            job.lastError="Invio da riprovare: consultare il log";
            org.slf4j.LoggerFactory.getLogger(getClass()).error("Conferma ordine {} da reinviare",id,ex);
        }
    }
}
