package com.delta.store.product.service;

import com.delta.store.product.dto.ProductCodeLookupDTO;
import com.delta.store.product.model.ArticleCodeType;
import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductCode;
import com.delta.store.product.model.ProductVariant;
import com.delta.store.product.repository.ProductCodeRepository;
import com.delta.store.product.repository.ProductRepository;
import com.delta.store.product.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductCodeServiceImpl implements ProductCodeService {

    private final ProductCodeRepository productCodeRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    public ProductCodeServiceImpl(ProductCodeRepository productCodeRepository,
                                  ProductRepository productRepository,
                                  ProductVariantRepository productVariantRepository) {
        this.productCodeRepository = productCodeRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @Override
    public void validateProductCodes(Long productId, String primaryCode, String additionalCodesText) {
        validateCodes(productId, null, primaryCode, parseCodes(additionalCodesText));
    }

    @Override
    public void validateVariantCodes(Long variantId, String primaryCode, String additionalCodesText) {
        validateCodes(null, variantId, primaryCode, parseCodes(additionalCodesText));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int replaceProductCodes(Product product, String additionalCodesText) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("Prodotto non valido per la gestione dei codici.");
        }

        Long productId = product.getId();
        List<ParsedCode> parsedCodes = parseCodes(additionalCodesText);
        validateCodes(productId, null, product.getCodpro(), parsedCodes);

        Product managedProduct = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Prodotto non trovato: " + productId));

        productCodeRepository.deleteAllByProductId(productId);
        productCodeRepository.flush();

        for (ParsedCode parsedCode : parsedCodes) {
            ProductCode code = new ProductCode();
            code.setProduct(managedProduct);
            code.setVariant(null);
            code.setCodeType(parsedCode.type());
            code.setCodeValue(parsedCode.value());
            productCodeRepository.save(code);
        }

        productCodeRepository.flush();
        verifyProductCodesPersisted(productId, parsedCodes);
        return parsedCodes.size();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int replaceVariantCodes(ProductVariant variant, String additionalCodesText) {
        if (variant == null || variant.getId() == null) {
            throw new IllegalArgumentException("Variante non valida per la gestione dei codici.");
        }

        Long variantId = variant.getId();
        List<ParsedCode> parsedCodes = parseCodes(additionalCodesText);
        validateCodes(null, variantId, variant.getVariantCode(), parsedCodes);

        ProductVariant managedVariant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variante non trovata: " + variantId));

        productCodeRepository.deleteAllByVariantId(variantId);
        productCodeRepository.flush();

        for (ParsedCode parsedCode : parsedCodes) {
            ProductCode code = new ProductCode();
            code.setProduct(null);
            code.setVariant(managedVariant);
            code.setCodeType(parsedCode.type());
            code.setCodeValue(parsedCode.value());
            productCodeRepository.save(code);
        }

        productCodeRepository.flush();
        verifyVariantCodesPersisted(variantId, parsedCodes);
        return parsedCodes.size();
    }

    @Override
    public String formatProductCodes(Long productId) {
        return formatCodes(findProductCodes(productId));
    }

    @Override
    public String formatVariantCodes(Long variantId) {
        return formatCodes(findVariantCodes(variantId));
    }

    @Override
    public List<ProductCode> findProductCodes(Long productId) {
        if (productId == null) {
            return List.of();
        }
        return productCodeRepository.findByProduct_IdOrderByCodeTypeAscCodeValueAsc(productId);
    }

    @Override
    public List<ProductCode> findVariantCodes(Long variantId) {
        if (variantId == null) {
            return List.of();
        }
        return productCodeRepository.findByVariant_IdOrderByCodeTypeAscCodeValueAsc(variantId);
    }

    @Override
    public Optional<ProductCodeLookupDTO> lookup(String code) {
        String normalized = ProductCode.normalizeCode(code);
        if (normalized == null) {
            return Optional.empty();
        }

        Optional<ProductCode> additionalCode = productCodeRepository.findByNormalizedCode(normalized);
        if (additionalCode.isPresent()) {
            return Optional.of(toLookupDto(code, normalized, additionalCode.get()));
        }

        String trimmedCode = code.trim();

        Optional<ProductVariant> variant = productVariantRepository
                .findWithDetailsByVariantCodeIgnoreCase(trimmedCode);
        if (variant.isPresent()) {
            return Optional.of(toLookupDto(
                    code,
                    normalized,
                    variant.get(),
                    ArticleCodeType.INTERNAL,
                    variant.get().getVariantCode()
            ));
        }

        return productRepository.findByCodproIgnoreCase(trimmedCode)
                .map(product -> toLookupDto(
                        code,
                        normalized,
                        product,
                        ArticleCodeType.INTERNAL,
                        product.getCodpro()
                ));
    }

    private void verifyProductCodesPersisted(Long productId, List<ParsedCode> expectedCodes) {
        List<ProductCode> persisted = productCodeRepository
                .findByProduct_IdOrderByCodeTypeAscCodeValueAsc(productId);
        verifyExactCodes(expectedCodes, persisted, "prodotto");
    }

    private void verifyVariantCodesPersisted(Long variantId, List<ParsedCode> expectedCodes) {
        List<ProductCode> persisted = productCodeRepository
                .findByVariant_IdOrderByCodeTypeAscCodeValueAsc(variantId);
        verifyExactCodes(expectedCodes, persisted, "variante");
    }

    private void verifyExactCodes(List<ParsedCode> expectedCodes,
                                  List<ProductCode> persistedCodes,
                                  String ownerLabel) {

        Set<String> expected = expectedCodes.stream()
                .map(ParsedCode::normalized)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> persisted = persistedCodes.stream()
                .map(ProductCode::getNormalizedCode)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!persisted.equals(expected)) {
            throw new IllegalStateException(
                    "Verifica codici " + ownerLabel + " non riuscita. Attesi: "
                            + expected + "; salvati: " + persisted + "."
            );
        }
    }

    private void validateCodes(Long currentProductId,
                               Long currentVariantId,
                               String primaryCode,
                               List<ParsedCode> additionalCodes) {

        String normalizedPrimary = ProductCode.normalizeCode(primaryCode);
        if (normalizedPrimary == null) {
            throw new IllegalArgumentException("Il codice interno principale è obbligatorio.");
        }

        Map<String, String> submitted = new LinkedHashMap<>();
        submitted.put(normalizedPrimary, primaryCode.trim());

        for (ParsedCode parsedCode : additionalCodes) {
            String previous = submitted.putIfAbsent(parsedCode.normalized(), parsedCode.value());
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Il codice alternativo " + parsedCode.value()
                                + " coincide con un altro codice inserito nella stessa scheda."
                );
            }
        }

        for (Map.Entry<String, String> entry : submitted.entrySet()) {
            validateAvailability(entry.getKey(), entry.getValue(), currentProductId, currentVariantId);
        }
    }

    private void validateAvailability(String normalized,
                                      String original,
                                      Long currentProductId,
                                      Long currentVariantId) {

        productCodeRepository.findByNormalizedCode(normalized).ifPresent(existing -> {
            boolean sameProductScope = currentProductId != null
                    && existing.getProduct() != null
                    && currentProductId.equals(existing.getProduct().getId());

            boolean sameVariantScope = currentVariantId != null
                    && existing.getVariant() != null
                    && currentVariantId.equals(existing.getVariant().getId());

            if (!sameProductScope && !sameVariantScope) {
                throw new IllegalArgumentException(
                        "Il codice " + original + " è già assegnato a un altro articolo."
                );
            }
        });

        productRepository.findByCodproIgnoreCase(original.trim()).ifPresent(existing -> {
            if (currentProductId == null || !currentProductId.equals(existing.getId())) {
                throw new IllegalArgumentException(
                        "Il codice " + original + " coincide con il codice interno di un altro prodotto."
                );
            }
        });

        productVariantRepository.findByVariantCodeIgnoreCase(original.trim()).ifPresent(existing -> {
            if (currentVariantId == null || !currentVariantId.equals(existing.getId())) {
                throw new IllegalArgumentException(
                        "Il codice " + original + " coincide con il codice interno di un'altra variante."
                );
            }
        });
    }

    private List<ParsedCode> parseCodes(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        Map<String, ParsedCode> unique = new LinkedHashMap<>();

        String normalizedText = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u2028', '\n')
                .replace('\u2029', '\n')
                .replace(';', '\n')
                .replace('|', '\n')
                .replace('\t', '\n');

        for (String rawLine : normalizedText.split("\\n+")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank()) {
                continue;
            }

            ParsedCode parsedCode = parseLine(line);
            ParsedCode previous = unique.putIfAbsent(parsedCode.normalized(), parsedCode);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Codice alternativo duplicato: " + parsedCode.value() + "."
                );
            }
        }

        return List.copyOf(unique.values());
    }

    private ParsedCode parseLine(String line) {
        String typeToken = null;
        String value = line;

        int colonIndex = line.indexOf(':');
        int equalIndex = line.indexOf('=');
        int separatorIndex;

        if (colonIndex < 0) {
            separatorIndex = equalIndex;
        } else if (equalIndex < 0) {
            separatorIndex = colonIndex;
        } else {
            separatorIndex = Math.min(colonIndex, equalIndex);
        }

        if (separatorIndex > 0) {
            String candidateType = line.substring(0, separatorIndex).trim();
            ArticleCodeType parsedType = parseType(candidateType);
            if (parsedType != null) {
                typeToken = candidateType;
                value = line.substring(separatorIndex + 1).trim();
            }
        }

        if (value.isBlank()) {
            throw new IllegalArgumentException("Codice alternativo vuoto nella riga: " + line);
        }

        ArticleCodeType type = typeToken != null ? parseType(typeToken) : detectType(value);
        String normalized = ProductCode.normalizeCode(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Codice alternativo non valido: " + value);
        }

        return new ParsedCode(
                type != null ? type : ArticleCodeType.OTHER,
                value,
                normalized
        );
    }

    private ArticleCodeType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");

        return switch (normalized) {
            case "INTERNO", "INTERNAL", "CODICEINTERNO", "SKU" -> ArticleCodeType.INTERNAL;
            case "EAN" -> ArticleCodeType.EAN;
            case "EAN13" -> ArticleCodeType.EAN13;
            case "EAN8" -> ArticleCodeType.EAN8;
            case "UPC", "UPCA", "UPCE" -> ArticleCodeType.UPC;
            case "BARCODE", "CODICEABARRE" -> ArticleCodeType.BARCODE;
            case "FORNITORE", "SUPPLIER", "CODICEFORNITORE" -> ArticleCodeType.SUPPLIER;
            case "ALTRO", "OTHER" -> ArticleCodeType.OTHER;
            default -> null;
        };
    }

    private ArticleCodeType detectType(String value) {
        String normalized = ProductCode.normalizeCode(value);
        if (normalized == null || !normalized.matches("\\d+")) {
            return ArticleCodeType.OTHER;
        }

        return switch (normalized.length()) {
            case 13 -> ArticleCodeType.EAN13;
            case 8 -> ArticleCodeType.EAN8;
            case 12 -> ArticleCodeType.UPC;
            default -> ArticleCodeType.BARCODE;
        };
    }

    private String formatCodes(List<ProductCode> codes) {
        if (codes == null || codes.isEmpty()) {
            return "";
        }

        return codes.stream()
                .map(code -> code.getCodeType().name() + ":" + code.getCodeValue())
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private ProductCodeLookupDTO toLookupDto(String requested,
                                             String normalized,
                                             ProductCode code) {
        if (code.getVariant() != null) {
            return toLookupDto(
                    requested,
                    normalized,
                    code.getVariant(),
                    code.getCodeType(),
                    code.getCodeValue()
            );
        }

        return toLookupDto(
                requested,
                normalized,
                code.getProduct(),
                code.getCodeType(),
                code.getCodeValue()
        );
    }

    private ProductCodeLookupDTO toLookupDto(String requested,
                                             String normalized,
                                             Product product,
                                             ArticleCodeType type,
                                             String matchedCode) {
        return new ProductCodeLookupDTO(
                requested,
                normalized,
                matchedCode,
                type.name(),
                "PRODUCT",
                product.getId(),
                product.getPublicId(),
                product.getCodpro(),
                product.getName(),
                null,
                null,
                product.getPrice(),
                product.getStockQty(),
                true,
                product.getImage()
        );
    }

    private ProductCodeLookupDTO toLookupDto(String requested,
                                             String normalized,
                                             ProductVariant variant,
                                             ArticleCodeType type,
                                             String matchedCode) {
        Product product = variant.getProduct();
        BigDecimal price = variant.getPrice() != null
                ? variant.getPrice()
                : (product != null ? product.getPrice() : null);
        String image = variant.getMainImage() != null && !variant.getMainImage().isBlank()
                ? variant.getMainImage()
                : (product != null ? product.getImage() : null);

        return new ProductCodeLookupDTO(
                requested,
                normalized,
                matchedCode,
                type.name(),
                "VARIANT",
                product != null ? product.getId() : null,
                product != null ? product.getPublicId() : null,
                product != null ? product.getCodpro() : null,
                product != null ? product.getName() : null,
                variant.getId(),
                variant.getVariantCode(),
                price,
                variant.getStockQty(),
                variant.isEnabled(),
                image
        );
    }

    private record ParsedCode(ArticleCodeType type, String value, String normalized) {
    }
}
