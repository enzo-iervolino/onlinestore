package com.delta.store.utility;

import com.delta.store.product.dto.ProductDTO;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

@Component
public class SampleProductImportMapper implements ImportRowMapper<ProductDTO> {

    @Override
    public ProductDTO fromColumns(String[] columns, int rowNumber) {
        if (columns.length < 8) {
            throw new IllegalArgumentException("Riga " + rowNumber + " non valida: servono 8 colonne");
        }

        String codpro = columns[0].trim();
        String name = columns[1].trim();
        Long categoryId = parseLong(columns[2], rowNumber, "categoryId");
        Integer stockQty = parseInteger(columns[3], rowNumber, "stockQty");
        String description = columns[4].trim();
        String materials = columns[5].trim();
        String shippingInfo = columns[6].trim();
        BigDecimal price = parseBigDecimal(columns[7], rowNumber, "price");

        validateProduct(codpro, name, categoryId, stockQty, price, rowNumber, "Riga");

        ProductDTO dto = new ProductDTO();
        dto.setCodpro(codpro);
        dto.setName(name);
        dto.setCategoryId(categoryId);
        dto.setStockQty(stockQty);
        dto.setDescription(description);
        dto.setMaterials(materials);
        dto.setShippingInfo(shippingInfo);
        dto.setPrice(price);

        return dto;
    }

    @Override
    public ProductDTO fromJsonNode(JsonNode node, int rowNumber) {
        String codpro = node.path("codpro").asText(null);
        String name = node.path("name").asText(null);
        Long categoryId = node.path("categoryId").isMissingNode() || node.path("categoryId").isNull()
                ? null
                : node.path("categoryId").asLong();
        Integer stockQty = node.path("stockQty").isMissingNode() || node.path("stockQty").isNull()
                ? null
                : node.path("stockQty").asInt();
        String description = node.path("description").asText("");
        String materials = node.path("materials").asText("");
        String shippingInfo = node.path("shippingInfo").asText("");
        BigDecimal price = new BigDecimal(node.path("price").asText("0").trim());

        validateProduct(codpro, name, categoryId, stockQty, price, rowNumber, "Elemento JSON");

        ProductDTO dto = new ProductDTO();
        dto.setCodpro(codpro.trim());
        dto.setName(name.trim());
        dto.setCategoryId(categoryId);
        dto.setStockQty(stockQty);
        dto.setDescription(description);
        dto.setMaterials(materials);
        dto.setShippingInfo(shippingInfo);
        dto.setPrice(price);

        return dto;
    }

    private void validateProduct(
            String codpro,
            String name,
            Long categoryId,
            Integer stockQty,
            BigDecimal price,
            int rowNumber,
            String label
    ) {
        if (codpro == null || codpro.isBlank()) {
            throw new IllegalArgumentException(label + " " + rowNumber + ": codpro obbligatorio");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(label + " " + rowNumber + ": name obbligatorio");
        }

        if (categoryId == null) {
            throw new IllegalArgumentException(label + " " + rowNumber + ": categoryId obbligatorio");
        }

        if (categoryId < 1 || categoryId > 4) {
            throw new IllegalArgumentException(label + " " + rowNumber + ": categoryId deve essere 1, 2, 3 oppure 4");
        }

        if (stockQty == null) {
            throw new IllegalArgumentException(label + " " + rowNumber + ": stockQty obbligatorio");
        }

        if (stockQty < 0) {
            throw new IllegalArgumentException(label + " " + rowNumber + ": stockQty non può essere negativo");
        }

        if (price == null) {
            throw new IllegalArgumentException(label + " " + rowNumber + ": price obbligatorio");
        }

        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(label + " " + rowNumber + ": price non può essere negativo");
        }
    }

    private Long parseLong(String value, int rowNumber, String fieldName) {
        try {
            if (value == null || value.trim().isBlank()) {
                return null;
            }
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Riga " + rowNumber + ": " + fieldName + " non valido");
        }
    }

    private Integer parseInteger(String value, int rowNumber, String fieldName) {
        try {
            if (value == null || value.trim().isBlank()) {
                return null;
            }
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Riga " + rowNumber + ": " + fieldName + " non valido");
        }
    }

    private BigDecimal parseBigDecimal(String value, int rowNumber, String fieldName) {
        try {
            if (value == null || value.trim().isBlank()) {
                return null;
            }

            String normalized = value.trim().replace(",", ".");
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Riga " + rowNumber + ": " + fieldName + " non valido");
        }
    }
}