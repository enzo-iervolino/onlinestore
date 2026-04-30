package com.delta.store.product.service;

import com.delta.store.product.service.VideoGenerationService;
import com.delta.store.product.model.ColorOption;
import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductVariant;
import com.delta.store.product.model.ProductVariantImage;
import com.delta.store.product.model.SizeOption;
import com.delta.store.product.repository.ProductVariantImageRepository;
import com.delta.store.product.repository.ProductVariantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductVariantServiceImpl implements ProductVariantService {

    private static final Logger log = LoggerFactory.getLogger(ProductVariantServiceImpl.class);
    private static final Path VARIANT_UPLOAD_DIR = Paths.get("uploads", "variants");

    private final ProductVariantRepository variantRepository;
    private final ProductVariantImageRepository variantImageRepository;
    private final ColorOptionService colorOptionService;
    private final SizeOptionService sizeOptionService;
    private final VideoGenerationService videoGenerationService;

    @PersistenceContext
    private EntityManager entityManager;

    public ProductVariantServiceImpl(ProductVariantRepository variantRepository,
                                     ProductVariantImageRepository variantImageRepository,
                                     ColorOptionService colorOptionService,
                                     SizeOptionService sizeOptionService,
                                     VideoGenerationService videoGenerationService) {
        this.variantRepository = variantRepository;
        this.variantImageRepository = variantImageRepository;
        this.colorOptionService = colorOptionService;
        this.sizeOptionService = sizeOptionService;
        this.videoGenerationService = videoGenerationService;
    }

    @Override
    public int createMissingVariants(Product product,
                                     List<String> variantCodes,
                                     List<Long> colorIds,
                                     List<Long> sizeIds) {

        if (product == null || product.getId() == null) {
            log.debug("createMissingVariants: product null o senza ID");
            return 0;
        }
        if (variantCodes == null || colorIds == null || sizeIds == null) {
            log.debug("createMissingVariants: liste null (codes={}, colors={}, sizes={})",
                    (variantCodes == null), (colorIds == null), (sizeIds == null));
            return 0;
        }

        int len = Math.min(variantCodes.size(), Math.min(colorIds.size(), sizeIds.size()));
        if (len <= 0) {
            log.debug("createMissingVariants: len<=0 (codes={}, colors={}, sizes={})",
                    variantCodes.size(), colorIds.size(), sizeIds.size());
            return 0;
        }

        List<ProductVariant> toCreate = new ArrayList<>();

        for (int i = 0; i < len; i++) {
            String code = variantCodes.get(i);
            Long colorId = colorIds.get(i);
            Long sizeId = sizeIds.get(i);

            if (code == null || code.isBlank()) continue;
            if (colorId == null || sizeId == null) continue;

            String normalizedCode = code.trim();

            if (variantRepository.existsByVariantCodeIgnoreCase(normalizedCode)) {
                continue;
            }

            Optional<ColorOption> colorOpt = colorOptionService.findById(colorId);
            Optional<SizeOption> sizeOpt = sizeOptionService.findById(sizeId);
            if (colorOpt.isEmpty() || sizeOpt.isEmpty()) {
                continue;
            }

            ColorOption color = colorOpt.get();
            SizeOption size = sizeOpt.get();

            if (variantRepository.existsByProductAndColorAndSize(product, color, size)) {
                continue;
            }

            ProductVariant v = new ProductVariant();
            v.setProduct(product);
            v.setVariantCode(normalizedCode);
            v.setColor(color);
            v.setSize(size);
            v.setEnabled(true);
            v.setStockQty(0);

            ProductVariant sameColorSource = findSameColorSource(product.getId(), color.getId(), null);
            if (sameColorSource != null) {
                copyMediaWithoutDeletingSource(sameColorSource, v);
            }

            toCreate.add(v);
        }

        if (toCreate.isEmpty()) {
            log.debug("createMissingVariants: nulla da creare");
            return 0;
        }

        variantRepository.saveAll(toCreate);
        log.debug("createMissingVariants: create {} varianti per productId={}", toCreate.size(), product.getId());
        return toCreate.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariant> findByProductId(Long productId) {
        if (productId == null) return Collections.emptyList();
        return variantRepository.findByProductIdOrderByVariantCodeAsc(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductVariant> findByIdForEdit(Long variantId) {
        if (variantId == null) return Optional.empty();
        return variantRepository.findWithDetailsById(variantId);
    }

    @Override
    public ProductVariant createVariant(Long productId,
                                        Long colorId,
                                        Long sizeId,
                                        String variantCode,
                                        BigDecimal price,
                                        Integer stockQty,
                                        boolean enabled,
                                        MultipartFile mainImageFile,
                                        boolean generateMainWebmFromImage,
                                        List<MultipartFile> galleryFiles) {

        if (productId == null) {
            throw new IllegalArgumentException("Il prodotto è obbligatorio.");
        }
        if (colorId == null) {
            throw new IllegalArgumentException("Il colore è obbligatorio.");
        }
        if (sizeId == null) {
            throw new IllegalArgumentException("La taglia è obbligatoria.");
        }

        String normalizedCode = normalize(variantCode);
        if (normalizedCode == null) {
            throw new IllegalArgumentException("Il codice variante è obbligatorio.");
        }

        if (variantRepository.existsByVariantCodeIgnoreCase(normalizedCode)) {
            throw new IllegalArgumentException("Esiste già una variante con codice: " + normalizedCode);
        }

        Product product = entityManager.find(Product.class, productId);
        if (product == null) {
            throw new IllegalArgumentException("Prodotto non trovato: " + productId);
        }

        ColorOption color = colorOptionService.findById(colorId)
                .orElseThrow(() -> new IllegalArgumentException("Colore non trovato: " + colorId));

        SizeOption size = sizeOptionService.findById(sizeId)
                .orElseThrow(() -> new IllegalArgumentException("Taglia non trovata: " + sizeId));

        if (variantRepository.existsByProductAndColorAndSize(product, color, size)) {
            throw new IllegalArgumentException("Esiste già una variante per questo prodotto con colore/taglia selezionati.");
        }

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setColor(color);
        variant.setSize(size);
        variant.setVariantCode(normalizedCode);
        variant.setPrice(price);
        variant.setStockQty(stockQty != null ? stockQty : 0);
        variant.setEnabled(enabled);

        ensureUploadDir();

        boolean hasNewMainImage = mainImageFile != null && !mainImageFile.isEmpty();
        boolean hasNewGallery = galleryFiles != null && galleryFiles.stream().anyMatch(f -> f != null && !f.isEmpty());

        if (hasNewMainImage) {
            String storedMainImageUrl = storeFile(mainImageFile);
            applyMainMedia(variant, mainImageFile, storedMainImageUrl, generateMainWebmFromImage);
        }

        if (hasNewGallery) {
            int sortOrder = 1;
            for (MultipartFile file : galleryFiles) {
                if (file == null || file.isEmpty()) {
                    continue;
                }

                ProductVariantImage image = new ProductVariantImage();
                image.setImageUrl(storeFile(file));
                image.setAltText(buildSharedAltText(product, color));
                image.setSortOrder(sortOrder++);
                image.setMediaType(resolveMediaType(file.getOriginalFilename()));
                image.setPosterUrl(null);
                variant.addImage(image);
            }
        }

        if (!hasNewMainImage && !hasNewGallery) {
            ProductVariant sameColorSource = findSameColorSource(productId, colorId, null);
            if (sameColorSource != null) {
                copyMediaWithoutDeletingSource(sameColorSource, variant);
            }
        }

        ProductVariant saved = variantRepository.save(variant);

        if (hasNewMainImage || hasNewGallery) {
            syncMediaAcrossSameColorVariants(saved);
        }

        log.debug("createVariant: variante {} creata per productId={}", saved.getId(), productId);
        return saved;
    }

    @Override
    public ProductVariant updateVariant(Long variantId,
                                        String variantCode,
                                        BigDecimal price,
                                        Integer stockQty,
                                        boolean enabled,
                                        MultipartFile mainImageFile,
                                        boolean generateMainWebmFromImage,
                                        List<MultipartFile> galleryFiles,
                                        List<Long> removeImageIds) {

        ProductVariant variant = variantRepository.findWithDetailsById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variante non trovata: " + variantId));

        String normalizedCode = normalize(variantCode);
        if (normalizedCode == null) {
            throw new IllegalArgumentException("Il codice variante è obbligatorio.");
        }

        if (!normalizedCode.equalsIgnoreCase(variant.getVariantCode())
                && variantRepository.existsByVariantCodeIgnoreCase(normalizedCode)) {
            throw new IllegalArgumentException("Esiste già una variante con codice: " + normalizedCode);
        }

        variant.setVariantCode(normalizedCode);
        variant.setPrice(price);
        variant.setStockQty(stockQty != null ? stockQty : 0);
        variant.setEnabled(enabled);

        ensureUploadDir();

        if (mainImageFile != null && !mainImageFile.isEmpty()) {
            String oldMainImage = variant.getMainImage();
            String newMainImageUrl = storeFile(mainImageFile);
            applyMainMedia(variant, mainImageFile, newMainImageUrl, generateMainWebmFromImage);
            deletePhysicalFileIfUnusedByOtherVariants(oldMainImage, variant.getId());
        }
        if (removeImageIds != null && !removeImageIds.isEmpty()) {
            Set<Long> idsToRemove = new HashSet<>(removeImageIds);

            List<ProductVariantImage> toRemove = variant.getImages()
                    .stream()
                    .filter(img -> img.getId() != null && idsToRemove.contains(img.getId()))
                    .collect(Collectors.toList());

            for (ProductVariantImage img : toRemove) {
                String oldUrl = img.getImageUrl();
                variant.removeImage(img);
                deletePhysicalFileIfUnusedByOtherGalleryVariants(oldUrl, variant.getId());
            }
        }

        int nextSortOrder = variant.getImages().stream()
                .map(ProductVariantImage::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        if (galleryFiles != null) {
            for (MultipartFile file : galleryFiles) {
                if (file == null || file.isEmpty()) {
                    continue;
                }

                String imageUrl = storeFile(file);

                ProductVariantImage image = new ProductVariantImage();
                image.setImageUrl(imageUrl);
                image.setAltText(buildSharedAltText(variant.getProduct(), variant.getColor()));
                image.setSortOrder(nextSortOrder++);
                image.setMediaType(resolveMediaType(file.getOriginalFilename()));
                image.setPosterUrl(null);

                variant.addImage(image);
            }
        }

        ProductVariant saved = variantRepository.save(variant);
        syncMediaAcrossSameColorVariants(saved);

        log.debug("updateVariant: variante {} aggiornata", saved.getId());
        return saved;
    }

    @Override
    public void deleteVariant(Long variantId) {
        ProductVariant variant = variantRepository.findWithDetailsById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variante non trovata: " + variantId));

        Long productId = variant.getProduct() != null ? variant.getProduct().getId() : null;
        Long colorId = variant.getColor() != null ? variant.getColor().getId() : null;
        String deletedMainImage = variant.getMainImage();

        List<String> deletedGalleryUrls = variant.getImages()
                .stream()
                .map(ProductVariantImage::getImageUrl)
                .filter(Objects::nonNull)
                .toList();

        variantRepository.delete(variant);
        variantRepository.flush();

        deletePhysicalFileIfUnusedByOtherVariants(deletedMainImage, variantId);
        for (String url : deletedGalleryUrls) {
            deletePhysicalFileIfUnusedByOtherGalleryVariants(url, variantId);
        }

        if (productId != null && colorId != null) {
            List<ProductVariant> remainingSameColor = variantRepository.findByProductIdAndColorIdOrderByVariantCodeAsc(productId, colorId);
            if (!remainingSameColor.isEmpty()) {
                ProductVariant source = remainingSameColor.get(0);
                syncMediaAcrossSameColorVariants(source);
            }
        }

        log.debug("deleteVariant: variante {} eliminata", variantId);
    }

    private ProductVariant findSameColorSource(Long productId, Long colorId, Long excludeVariantId) {
        if (productId == null || colorId == null) {
            return null;
        }

        List<ProductVariant> sameColorVariants = variantRepository.findByProductIdAndColorIdOrderByVariantCodeAsc(productId, colorId);

        return sameColorVariants.stream()
                .filter(v -> excludeVariantId == null || !Objects.equals(v.getId(), excludeVariantId))
                .findFirst()
                .orElse(null);
    }

    private void syncMediaAcrossSameColorVariants(ProductVariant source) {
        if (source == null || source.getProduct() == null || source.getColor() == null) {
            return;
        }

        List<ProductVariant> sameColorVariants = variantRepository.findByProductIdAndColorIdOrderByVariantCodeAsc(
                source.getProduct().getId(),
                source.getColor().getId()
        );

        for (ProductVariant target : sameColorVariants) {
            if (Objects.equals(target.getId(), source.getId())) {
                normalizeAltTexts(source);
                continue;
            }

            replaceMediaFromSource(source, target);
        }

        variantRepository.saveAll(sameColorVariants);
    }

    private void replaceMediaFromSource(ProductVariant source, ProductVariant target) {
        String oldMainImage = target.getMainImage();
        List<ProductVariantImage> oldImages = new ArrayList<>(target.getImages());

        target.setMainImage(source.getMainImage());
        target.setMainMediaType(source.getMainMediaType());
        target.setMainVideoPoster(source.getMainVideoPoster());

        for (ProductVariantImage oldImage : oldImages) {
            target.removeImage(oldImage);
        }

        for (ProductVariantImage sourceImage : source.getImages()) {
            ProductVariantImage clone = new ProductVariantImage();
            clone.setImageUrl(sourceImage.getImageUrl());
            clone.setAltText(sourceImage.getAltText());
            clone.setSortOrder(sourceImage.getSortOrder());
            clone.setMediaType(sourceImage.getMediaType());
            clone.setPosterUrl(sourceImage.getPosterUrl());
            target.addImage(clone);
        }

        deletePhysicalFileIfUnusedByOtherVariants(oldMainImage, target.getId());
        for (ProductVariantImage oldImage : oldImages) {
            deletePhysicalFileIfUnusedByOtherGalleryVariants(oldImage.getImageUrl(), target.getId());
        }
    }

    private void copyMediaWithoutDeletingSource(ProductVariant source, ProductVariant target) {
        target.setMainImage(source.getMainImage());
        target.setMainMediaType(source.getMainMediaType());
        target.setMainVideoPoster(source.getMainVideoPoster());

        for (ProductVariantImage sourceImage : source.getImages()) {
            ProductVariantImage clone = new ProductVariantImage();
            clone.setImageUrl(sourceImage.getImageUrl());
            clone.setAltText(sourceImage.getAltText());
            clone.setSortOrder(sourceImage.getSortOrder());
            clone.setMediaType(sourceImage.getMediaType());
            clone.setPosterUrl(sourceImage.getPosterUrl());
            target.addImage(clone);
        }
    }

    private void normalizeAltTexts(ProductVariant source) {
        String altText = buildSharedAltText(source.getProduct(), source.getColor());

        if (source.getImages() != null) {
            for (ProductVariantImage img : source.getImages()) {
                img.setAltText(altText);
            }
        }
    }

    private String buildSharedAltText(Product product, ColorOption color) {
        String productCode = product != null && product.getCodpro() != null ? product.getCodpro().trim() : "PRODOTTO";
        String colorCode = color != null && color.getCode() != null ? color.getCode().trim() : "COLORE";
        return productCode + "-" + colorCode;
    }

    private void ensureUploadDir() {
        try {
            Files.createDirectories(VARIANT_UPLOAD_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Impossibile creare la cartella uploads/variants", e);
        }
    }

    private String storeFile(MultipartFile file) {
        String originalName = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        String extension = "";

        int idx = originalName.lastIndexOf('.');
        if (idx >= 0) {
            extension = originalName.substring(idx);
        }

        String generatedName = UUID.randomUUID() + extension;
        Path target = VARIANT_UPLOAD_DIR.resolve(generatedName);

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/variants/" + generatedName;
        } catch (IOException e) {
            throw new RuntimeException("Errore salvataggio file variante: " + originalName, e);
        }
    }

    private void applyMainMedia(ProductVariant variant, MultipartFile sourceFile, String storedUrl, boolean generateMainWebmFromImage) {
        if (generateMainWebmFromImage && videoGenerationService.isImageFile(sourceFile)) {
            try {
                Path storedImagePath = resolveStoredUploadPath(storedUrl);
                Path generatedWebmPath = videoGenerationService.generateWebmFromImage(storedImagePath, VARIANT_UPLOAD_DIR);
                variant.setMainImage(toVariantUploadUrl(generatedWebmPath));
                variant.setMainMediaType(ProductVariant.MAIN_MEDIA_TYPE_VIDEO);
                variant.setMainVideoPoster(storedUrl);
                return;
            } catch (RuntimeException generationException) {
                log.warn("Generazione WebM non riuscita per la variante id={}. Il media principale resta immagine: {}",
                        variant != null ? variant.getId() : null, generationException.getMessage());
            }
        }

        variant.setMainImage(storedUrl);
        variant.setMainMediaType(resolveVariantMainMediaType(sourceFile.getOriginalFilename()));
        variant.setMainVideoPoster(null);
    }

    private Path resolveStoredUploadPath(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) {
            throw new IllegalArgumentException("Percorso file non valido.");
        }

        String normalized = storedUrl.replace("\\", "/").trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        return Paths.get(normalized).toAbsolutePath().normalize();
    }

    private String toVariantUploadUrl(Path path) {
        if (path == null || path.getFileName() == null) {
            throw new IllegalArgumentException("Percorso WebM non valido.");
        }
        return "/uploads/variants/" + path.getFileName();
    }

    private String resolveMediaType(String filename) {
        if (filename == null) {
            return ProductVariantImage.MEDIA_TYPE_IMAGE;
        }

        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".ogg") || lower.endsWith(".mov")) {
            return ProductVariantImage.MEDIA_TYPE_VIDEO;
        }

        return ProductVariantImage.MEDIA_TYPE_IMAGE;
    }

    private String resolveVariantMainMediaType(String filename) {
        return ProductVariantImage.MEDIA_TYPE_VIDEO.equals(resolveMediaType(filename))
                ? ProductVariant.MAIN_MEDIA_TYPE_VIDEO
                : ProductVariant.MAIN_MEDIA_TYPE_IMAGE;
    }

    private void deletePhysicalFileIfUnusedByOtherVariants(String imageUrl, Long currentVariantId) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        long stillUsedByOtherMainImages = variantRepository.countByMainImageAndIdNot(imageUrl, currentVariantId);
        long stillUsedByOtherGalleryImages = variantImageRepository.countByImageUrlAndVariantIdNot(imageUrl, currentVariantId);

        if (stillUsedByOtherMainImages == 0 && stillUsedByOtherGalleryImages == 0) {
            deletePhysicalFile(imageUrl);
        }
    }

    private void deletePhysicalFileIfUnusedByOtherGalleryVariants(String imageUrl, Long currentVariantId) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        long stillUsedByOtherGalleryImages = variantImageRepository.countByImageUrlAndVariantIdNot(imageUrl, currentVariantId);
        long stillUsedByOtherMainImages = variantRepository.countByMainImageAndIdNot(imageUrl, currentVariantId);

        if (stillUsedByOtherGalleryImages == 0 && stillUsedByOtherMainImages == 0) {
            deletePhysicalFile(imageUrl);
        }
    }

    private void deletePhysicalFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        try {
            String normalized = imageUrl.replace("\\", "/").trim();

            if (normalized.startsWith("/uploads/")) {
                normalized = normalized.substring(1);
            }

            Path filePath = Paths.get(normalized);
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            log.warn("Impossibile eliminare il file fisico {}", imageUrl, e);
        }
    }

    private String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}