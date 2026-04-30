package com.delta.store.product.service;

import com.delta.store.product.dto.ProductDTO;
import com.delta.store.product.dto.ProductImageDTO;
import com.delta.store.product.dto.ProductVariantDTO;
import com.delta.store.product.dto.ShopProductDetailDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ShopProductDetailServiceImpl implements ShopProductDetailService {

    private static final String ONE_SIZE_CODE = "TU";
    private static final String ONE_SIZE_LABEL = "Taglia unica";
    private static final String ONE_COLOR_CODE = "ONE_COLOR";
    private static final String ONE_COLOR_LABEL = "Colore unico";

    private final ProductService productService;

    public ShopProductDetailServiceImpl(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public ShopProductDetailDTO buildDetail(Long productId) {
        ProductDTO product = productService.findWithImagesByIdDto(productId)
                .orElseThrow(() -> new RuntimeException("Prodotto non trovato"));

        ShopProductDetailDTO dto = new ShopProductDetailDTO();
        dto.setProductId(product.getId());
        dto.setProductCode(product.getCodpro());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setMaterials(product.getMaterials());
        dto.setShippingInfo(product.getShippingInfo());
        dto.setBasePrice(product.getPrice());
        dto.setMainImage(product.getImage());
        dto.setMainMediaType(product.getMainMediaType() != null ? product.getMainMediaType() : "IMAGE");
        dto.setMainVideoPoster(product.getMainVideoPoster());

        List<ProductImageDTO> orderedMedia = product.getImages().stream()
                .sorted(Comparator
                        .comparing(ProductImageDTO::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProductImageDTO::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        dto.setImages(new ArrayList<>(orderedMedia));

        List<ProductVariantDTO> enabledVariants = product.getVariants().stream()
                .filter(ProductVariantDTO::isEnabled)
                .sorted(Comparator.comparing(ProductVariantDTO::getId))
                .toList();

        dto.setVariants(enabledVariants);
        dto.setHasVariants(!enabledVariants.isEmpty());

        List<String> realSizes = enabledVariants.stream()
                .map(v -> normalizeSizeCode(v.getSizeCode()))
                .filter(code -> !ONE_SIZE_CODE.equals(code))
                .distinct()
                .toList();

        List<String> realColors = enabledVariants.stream()
                .map(v -> normalizeColorCode(v.getColorCode()))
                .filter(code -> !ONE_COLOR_CODE.equals(code))
                .distinct()
                .toList();

        dto.setShowSizeSelector(realSizes.size() > 1);
        dto.setShowColorSelector(realColors.size() > 1);

        if (!dto.isShowSizeSelector() && realSizes.size() == 1) {
            String label = enabledVariants.stream()
                    .map(ProductVariantDTO::getSizeName)
                    .filter(Objects::nonNull)
                    .filter(v -> !v.isBlank())
                    .findFirst()
                    .orElse(ONE_SIZE_LABEL);
            dto.setSingleSizeLabel(label);
        }

        if (!dto.isShowColorSelector() && realColors.size() == 1) {
            String label = enabledVariants.stream()
                    .map(ProductVariantDTO::getColorName)
                    .filter(Objects::nonNull)
                    .filter(v -> !v.isBlank())
                    .findFirst()
                    .orElse(ONE_COLOR_LABEL);
            dto.setSingleColorLabel(label);
        }

        ProductVariantDTO defaultVariant = enabledVariants.stream()
                .filter(v -> v.getStockQty() != null && v.getStockQty() > 0)
                .findFirst()
                .orElse(enabledVariants.stream().findFirst().orElse(null));

        if (defaultVariant != null) {
            dto.setDefaultVariantId(defaultVariant.getId());
            dto.setDefaultSizeCode(normalizeSizeCode(defaultVariant.getSizeCode()));
            dto.setDefaultColorCode(normalizeColorCode(defaultVariant.getColorCode()));
        }

        return dto;
    }

    private String normalizeSizeCode(String sizeCode) {
        if (sizeCode == null || sizeCode.isBlank()) {
            return ONE_SIZE_CODE;
        }
        return sizeCode.trim().toUpperCase();
    }

    private String normalizeColorCode(String colorCode) {
        if (colorCode == null || colorCode.isBlank()) {
            return ONE_COLOR_CODE;
        }
        return colorCode.trim().toUpperCase();
    }
}
