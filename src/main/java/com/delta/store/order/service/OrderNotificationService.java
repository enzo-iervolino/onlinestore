package com.delta.store.order.service;

import com.delta.store.order.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrderNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationService.class);

    private final ReceiptPdfService receiptPdfService;
    private final OrderEmailService orderEmailService;

    public OrderNotificationService(ReceiptPdfService receiptPdfService,
                                    OrderEmailService orderEmailService) {
        this.receiptPdfService = receiptPdfService;
        this.orderEmailService = orderEmailService;
    }

    public void sendOrderConfirmationAfterCommit(Order order) {
        if (order == null) {
            log.warn("Invio email ordine saltato: ordine nullo.");
            return;
        }

        if (order.getCustomerEmail() == null || order.getCustomerEmail().isBlank()) {
            log.warn("Invio email ordine #{} saltato: email cliente mancante.", order.getId());
            return;
        }

        byte[] pdf = null;
        try {
            pdf = receiptPdfService.generateReceipt(order);
        } catch (Exception e) {
            log.warn("PDF scontrino non generato per ordine #{}: {}. Procedo comunque con email senza allegato.", order.getId(), e.getMessage(), e);
        }

        byte[] finalPdf = pdf;

        Runnable sendTask = () -> {
            try {
                orderEmailService.sendOrderConfirmation(order.getCustomerEmail(), order, finalPdf);
                log.info("Email conferma ordine #{} inviata a {}", order.getId(), order.getCustomerEmail());
            } catch (Exception e) {
                log.error("Email conferma ordine #{} NON inviata a {}: {}", order.getId(), order.getCustomerEmail(), e.getMessage(), e);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendTask.run();
                }
            });
        } else {
            sendTask.run();
        }
    }
}
