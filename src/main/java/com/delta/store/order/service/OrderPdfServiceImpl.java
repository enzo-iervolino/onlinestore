package com.delta.store.order.service;

import com.delta.store.order.model.Order;
import com.delta.store.order.model.OrderItem;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class OrderPdfServiceImpl implements OrderPdfService {

    private static final Locale LOCALE_IT = Locale.ITALY;
    private static final NumberFormat EUR = NumberFormat.getCurrencyInstance(LOCALE_IT);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public byte[] generateOrderPdf(Order order) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font h = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);

            doc.add(new Paragraph("Ricevuta ordine #" + order.getId(), title));
            doc.add(Chunk.NEWLINE);

            String created = (order.getCreatedAt() != null) ? order.getCreatedAt().format(DT) : "—";
            doc.add(new Paragraph("Data: " + created, normal));
            doc.add(new Paragraph("Stato: " + safe(order.getStatus()), normal));
            doc.add(new Paragraph("Pagamento: " + detectPaymentMethod(order), normal));

            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Cliente", h));
            doc.add(new Paragraph(safe(order.getCustomerName()), normal));
            doc.add(new Paragraph(safe(order.getCustomerEmail()), normal));
            doc.add(new Paragraph(safe(order.getCustomerAddress()), normal));

            doc.add(Chunk.NEWLINE);

            // Tabella items
            PdfPTable itemsTable = new PdfPTable(new float[]{4.5f, 1.6f, 1.0f, 1.7f});
            itemsTable.setWidthPercentage(100);

            itemsTable.addCell(headerCell("Prodotto"));
            itemsTable.addCell(headerCell("Prezzo"));
            itemsTable.addCell(headerCell("Qtà"));
            itemsTable.addCell(headerCell("Totale"));

            List<OrderItem> items = (order.getItems() == null ? List.<OrderItem>of() : order.getItems().stream().toList())
                    .stream()
                    .sorted(Comparator.comparing(i -> i.getId() == null ? Long.MAX_VALUE : i.getId()))
                    .toList();

            if (items.isEmpty()) {
                PdfPCell c = new PdfPCell(new Phrase("Nessuna riga ordine.", normal));
                c.setColspan(4);
                c.setPadding(8);
                itemsTable.addCell(c);
            } else {
                for (OrderItem it : items) {
                    String name = safe(it.getProductName());
                    double unit = it.getUnitPrice() != null ? it.getUnitPrice() : 0.0;
                    int qty = it.getQuantity() != null ? it.getQuantity() : 0;
                    double line = unit * qty;

                    itemsTable.addCell(bodyCell(name));
                    itemsTable.addCell(bodyCell(EUR.format(unit), Element.ALIGN_RIGHT));
                    itemsTable.addCell(bodyCell(String.valueOf(qty), Element.ALIGN_CENTER));
                    itemsTable.addCell(bodyCell(EUR.format(line), Element.ALIGN_RIGHT));
                }
            }

            doc.add(itemsTable);
            doc.add(Chunk.NEWLINE);

            // Totali (a destra)
            PdfPTable totals = new PdfPTable(new float[]{2.0f, 1.2f});
            totals.setWidthPercentage(45);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);

            addTotalRow(totals, "Subtotale", order.getSubtotal());
            addTotalRow(totals, "Sconto", safeNeg(order.getDiscountValue()));
            addTotalRow(totals, "Spedizione", order.getShippingCost());

            PdfPCell sepL = new PdfPCell(new Phrase("Totale", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            PdfPCell sepR = new PdfPCell(new Phrase(EUR.format(safeBD(order.getTotalAmount())), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            sepL.setBorderWidthTop(1);
            sepR.setBorderWidthTop(1);
            sepL.setPadding(6);
            sepR.setPadding(6);
            sepR.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.addCell(sepL);
            totals.addCell(sepR);

            doc.add(totals);

            doc.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new IllegalStateException("Errore generazione PDF ordine #" + (order != null ? order.getId() : "—"), e);
        }
    }

    private static PdfPCell headerCell(String text) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setPadding(6);
        c.setBackgroundColor(new java.awt.Color(245, 245, 245));
        return c;
    }

    private static PdfPCell bodyCell(String text) {
        return bodyCell(text, Element.ALIGN_LEFT);
    }

    private static PdfPCell bodyCell(String text, int align) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 10);
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setPadding(6);
        c.setHorizontalAlignment(align);
        return c;
    }

    private static void addTotalRow(PdfPTable t, String label, BigDecimal value) {
        PdfPCell l = bodyCell(label);
        PdfPCell r = bodyCell(EUR.format(safeBD(value)), Element.ALIGN_RIGHT);
        t.addCell(l);
        t.addCell(r);
    }

    private static BigDecimal safeBD(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal safeNeg(BigDecimal v) {
        BigDecimal x = safeBD(v);
        return x.signum() > 0 ? x.negate() : x;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private static String detectPaymentMethod(Order o) {
        if (o == null) return "—";
        if (o.getStripeSessionId() != null && !o.getStripeSessionId().isBlank()) return "STRIPE";
        if (o.getGiftcardCode() != null && !o.getGiftcardCode().isBlank()) return "GIFTCARD";
        if (o.getSessionId() != null && !o.getSessionId().isBlank()) return "PAYPAL";
        return "—";
    }
}
