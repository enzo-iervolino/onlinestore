package com.delta.store.order.service;

import com.delta.store.order.model.Order;
import com.delta.store.order.model.OrderItem;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

@Service
public class ReceiptPdfService {

    public byte[] generateReceipt(Order order) {

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
            Font normalFont = new Font(Font.FontFamily.HELVETICA, 11);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD);

            // =============================
            // HEADER
            // =============================
            document.add(new Paragraph("ONLINE STORE", titleFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Scontrino Ordine", boldFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Ordine n°: " + order.getId(), normalFont));
            document.add(new Paragraph("Data: " +
                    order.getCreatedAt().format(
                            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    ), normalFont));
            document.add(new Paragraph("Cliente: " + order.getCustomerName(), normalFont));
            document.add(new Paragraph("Email: " + order.getCustomerEmail(), normalFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));

            // =============================
            // TABELLA PRODOTTI
            // =============================
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new int[]{4, 2, 2, 2});

            addHeaderCell(table, "Prodotto");
            addHeaderCell(table, "Prezzo");
            addHeaderCell(table, "Quantità");
            addHeaderCell(table, "Totale");

            for (OrderItem item : order.getItems()) {

                table.addCell(item.getProductName());
                table.addCell(formatCurrency(item.getUnitPrice()));
                table.addCell(String.valueOf(item.getQuantity()));
                table.addCell(formatCurrency(item.getLineTotal()));
            }

            document.add(table);

            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));

            // =============================
            // BREAKDOWN IMPORTI
            // =============================
            document.add(new Paragraph("Riepilogo Importi", boldFont));
            document.add(new Paragraph(" "));

            addAmountLine(document, "Subtotale:", order.getSubtotal());
            addAmountLine(document, "Sconto (%):", order.getDiscountPct());
            addAmountLine(document, "Valore Sconto:", order.getDiscountValue());
            addAmountLine(document, "Subtotale Netto:", order.getNetSubtotal());
            addAmountLine(document, "Spedizione:", order.getShippingCost());
            addAmountLine(document, "GiftCard Utilizzata:", order.getGiftcardAmount());

            document.add(new Paragraph(" "));
            document.add(new Paragraph(
                    "TOTALE FINALE: € " + formatCurrency(order.getTotalAmount()),
                    boldFont
            ));

            document.add(new Paragraph(" "));
            document.add(new Paragraph("Stato ordine: " + order.getStatus(), normalFont));

            document.close();

            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Errore generazione PDF", e);
        }
    }

    // =============================
    // Helper methods
    // =============================

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell header = new PdfPCell(new Phrase(text));
        header.setBackgroundColor(BaseColor.LIGHT_GRAY);
        table.addCell(header);
    }

    private void addAmountLine(Document document, String label, BigDecimal value) throws DocumentException {
        if (value != null) {
            document.add(new Paragraph(label + " € " + formatCurrency(value)));
        }
    }

    private String formatCurrency(Double value) {
        if (value == null) return "0.00";
        return String.format("%.2f", value);
    }

    private String formatCurrency(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

}
