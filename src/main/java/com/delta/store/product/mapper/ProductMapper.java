package com.delta.store.product.mapper;

import com.delta.store.product.dto.ProductDTO;
import com.delta.store.product.dto.ProductImageDTO;
import com.delta.store.product.dto.ProductVariantDTO;
import com.delta.store.product.dto.ProductVariantImageDTO;
import com.delta.store.product.model.ColorOption;
import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductCategory;
import com.delta.store.product.model.ProductImage;
import com.delta.store.product.model.ProductVariant;
import com.delta.store.product.model.ProductVariantImage;
import com.delta.store.product.model.SizeOption;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductDTO toDto(Product product) {
        if (product == null) {
            return null;
        }

        ProductDTO dto = new ProductDTO();

        dto.setId(product.getId());
        dto.setPublicId(product.getPublicId());
        dto.setCodpro(product.getCodpro());
        dto.setName(product.getName());

        dto.setStockQty(product.getStockQty());
        dto.setGroupId(product.getGroupId());
        dto.setSubgroupId(product.getSubgroupId());
        dto.setBrandId(product.getBrandId());
        dto.setFamilyId(product.getFamilyId());
        dto.setSubfamilyId(product.getSubfamilyId());

        dto.setDescription(product.getDescription());
        dto.setMaterials(product.getMaterials());
        dto.setShippingInfo(product.getShippingInfo());

        dto.setPrice(product.getPrice());
        dto.setImage(product.getImage());
        dto.setMainMediaType(product.getMainMediaType());
        dto.setMainVideoPoster(product.getMainVideoPoster());

        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());

        mapCategory(product.getCategory(), dto);
        dto.setImages(toProductImageDtoSet(product.getImages()));
        dto.setVariants(toProductVariantDtoSet(product.getVariants()));

        return dto;
    }

    public static List<ProductDTO> toDtoList(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }

        return products.stream()
                .map(ProductMapper::toDto)
                .toList();
    }

    private static void mapCategory(ProductCategory category, ProductDTO dto) {
        if (category == null) {
            return;
        }

        dto.setCategoryId(category.getId());
        dto.setCategoryCode(category.getCode());
        dto.setCategoryName(category.getName());
    }

    private static Set<ProductImageDTO> toProductImageDtoSet(Set<ProductImage> images) {
        Set<ProductImageDTO> result = new LinkedHashSet<>();
        if (images == null || images.isEmpty()) {
            return result;
        }

        for (ProductImage image : images) {
            ProductImageDTO dto = toDto(image);
            if (dto != null) {
                result.add(dto);
            }
        }

        return result;
    }

    private static ProductImageDTO toDto(ProductImage image) {
        if (image == null) {
            return null;
        }

        ProductImageDTO dto = new ProductImageDTO();
        dto.setId(image.getId());
        dto.setImageUrl(image.getImageUrl());
        dto.setAltText(image.getAltText());
        dto.setSortOrder(image.getSortOrder());
        dto.setMediaType(image.getMediaType());
        dto.setPosterUrl(image.getPosterUrl());
        return dto;
    }

    private static Set<ProductVariantDTO> toProductVariantDtoSet(Set<ProductVariant> variants) {
        Set<ProductVariantDTO> result = new LinkedHashSet<>();
        if (variants == null || variants.isEmpty()) {
            return result;
        }

        for (ProductVariant variant : variants) {
            ProductVariantDTO dto = toDto(variant);
            if (dto != null) {
                result.add(dto);
            }
        }

        return result;
    }

    private static ProductVariantDTO toDto(ProductVariant variant) {
        if (variant == null) {
            return null;
        }

        ProductVariantDTO dto = new ProductVariantDTO();

        dto.setId(variant.getId());
        dto.setVariantCode(variant.getVariantCode());
        dto.setPrice(variant.getPrice());
        dto.setStockQty(variant.getStockQty());
        dto.setMainImage(variant.getMainImage());
        dto.setMainMediaType(variant.getMainMediaType());
        dto.setMainVideoPoster(variant.getMainVideoPoster());
        dto.setEnabled(variant.isEnabled());
        dto.setCreatedAt(variant.getCreatedAt());
        dto.setUpdatedAt(variant.getUpdatedAt());

        mapColor(variant.getColor(), dto);
        mapSize(variant.getSize(), dto);
        dto.setImages(toProductVariantImageDtoSet(variant.getImages()));

        return dto;
    }

    private static void mapColor(ColorOption color, ProductVariantDTO dto) {
        if (color == null) {
            return;
        }

        dto.setColorId(color.getId());
        dto.setColorCode(color.getCode());
        dto.setColorName(color.getNameIt());
        dto.setColorHex(color.getHex());
        dto.setColorRgb(color.getRgb());
    }

    private static void mapSize(SizeOption size, ProductVariantDTO dto) {
        if (size == null) {
            return;
        }

        dto.setSizeId(size.getId());
        dto.setSizeCode(size.getCode());
        dto.setSizeName(size.getNameIt());
    }

    private static Set<ProductVariantImageDTO> toProductVariantImageDtoSet(Set<ProductVariantImage> images) {
        Set<ProductVariantImageDTO> result = new LinkedHashSet<>();
        if (images == null || images.isEmpty()) {
            return result;
        }

        for (ProductVariantImage image : images) {
            ProductVariantImageDTO dto = toDto(image);
            if (dto != null) {
                result.add(dto);
            }
        }

        return result;
    }

    private static ProductVariantImageDTO toDto(ProductVariantImage image) {
        if (image == null) {
            return null;
        }

        ProductVariantImageDTO dto = new ProductVariantImageDTO();
        dto.setId(image.getId());
        dto.setImageUrl(image.getImageUrl());
        dto.setAltText(image.getAltText());
        dto.setSortOrder(image.getSortOrder());
        dto.setMediaType(image.getMediaType());
        dto.setPosterUrl(image.getPosterUrl());
        return dto;
    }
}