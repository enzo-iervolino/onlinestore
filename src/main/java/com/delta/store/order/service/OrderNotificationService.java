package com.delta.store.order.service;
 

import com.delta.store.order.model.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrderNotificationService {

    private final ReceiptPdfService receiptPdfService;
    private final OrderEmailService orderEmailService;

    public OrderNotificationService(ReceiptPdfService receiptPdfService,
                                    OrderEmailService orderEmailService) {
        this.receiptPdfService = receiptPdfService;
        this.orderEmailService = orderEmailService;
    }

    public void sendOrderConfirmationAfterCommit(Order order) {

        // Genero il PDF DENTRO la transazione (così non ho problemi di LAZY items)
        byte[] pdf = null;
        try {
            pdf = receiptPdfService.generateReceipt(order);
        } catch (Exception ignored) {
            // se PDF fallisce non blocco l’ordine
        }

        byte[] finalPdf = pdf;

        Runnable sendTask = () -> {
            try {
                orderEmailService.sendOrderConfirmation(order.getCustomerEmail(), order, finalPdf);
            } catch (Exception ignored) {
                // non deve bloccare nulla
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
