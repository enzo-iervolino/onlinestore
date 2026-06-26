package com.delta.store.web.admin;

import com.delta.store.product.model.ImageLookupEntity;
import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductCategory;
import com.delta.store.product.model.ProductImage;
import com.delta.store.product.model.ProductVariant;
import com.delta.store.product.service.VideoGenerationService;
import com.delta.store.product.repository.ImageLookupRepository;
import com.delta.store.product.repository.ProductBrandRepository;
import com.delta.store.product.repository.ProductFamilyRepository;
import com.delta.store.product.repository.ProductGroupRepository;
import com.delta.store.product.repository.ProductSubfamilyRepository;
import com.delta.store.product.repository.ProductSubgroupRepository;
import com.delta.store.product.repository.ProductVariantRepository;
import com.delta.store.product.service.ColorOptionService;
import com.delta.store.product.service.ProductService;
import com.delta.store.product.service.ProductVariantService;
import com.delta.store.product.service.SizeOptionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/products")
public class AdminProductController {

    private static final Logger log = LoggerFactory.getLogger(AdminProductController.class);

    private final ProductService productService;
    private final ColorOptionService colorOptionService;
    private final SizeOptionService sizeOptionService;
    private final ProductVariantService productVariantService;
    private final ProductVariantRepository productVariantRepository;
    private final VideoGenerationService videoGenerationService;
    private final ProductGroupRepository groupRepository;
    private final ProductSubgroupRepository subgroupRepository;
    private final ProductBrandRepository brandRepository;
    private final ProductFamilyRepository familyRepository;
    private final ProductSubfamilyRepository subfamilyRepository;

    @Value("${app.upload.products-dir:uploads/products}")
    private String productsUploadDir;

    public AdminProductController(ProductService productService,
                                  ColorOptionService colorOptionService,
                                  SizeOptionService sizeOptionService,
                                  ProductVariantService productVariantService,
                                  ProductVariantRepository productVariantRepository,
                                  VideoGenerationService videoGenerationService,
                                  ProductGroupRepository groupRepository,
                                  ProductSubgroupRepository subgroupRepository,
                                  ProductBrandRepository brandRepository,
                                  ProductFamilyRepository familyRepository,
                                  ProductSubfamilyRepository subfamilyRepository) {
        this.productService = productService;
        this.colorOptionService = colorOptionService;
        this.sizeOptionService = sizeOptionService;
        this.productVariantService = productVariantService;
        this.productVariantRepository = productVariantRepository;
        this.videoGenerationService = videoGenerationService;
        this.groupRepository = groupRepository;
        this.subgroupRepository = subgroupRepository;
        this.brandRepository = brandRepository;
        this.familyRepository = familyRepository;
        this.subfamilyRepository = subfamilyRepository;
    }

    @GetMapping(value = "/{productId}/variants", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<ProductVariantRow> getVariants(@PathVariable Long productId) {
        List<ProductVariant> list = productVariantService.findByProductId(productId);

        return list.stream().map(v -> new ProductVariantRow(
                v.getId(),
                v.getVariantCode(),

                v.getColor() != null ? v.getColor().getId() : null,
                v.getColor() != null ? safe(v.getColor().getCode()) : null,
                v.getColor() != null ? safe(v.getColor().getNameIt()) : null,

                v.getSize() != null ? v.getSize().getId() : null,
                v.getSize() != null ? safe(v.getSize().getCode()) : null,
                v.getSize() != null ? safe(v.getSize().getNameIt()) : null,

                v.getPrice(),
                v.getStockQty(),
                v.isEnabled(),
                safe(v.getMainImage())
        )).toList();
    }

    @DeleteMapping(value = "/variants/{variantId}")
    @ResponseBody
    public ResponseEntity<Void> deleteVariant(@PathVariable Long variantId) {
        try {
            if (variantId == null) {
                return ResponseEntity.badRequest().build();
            }

            if (!productVariantRepository.existsById(variantId)) {
                return ResponseEntity.notFound().build();
            }

            productVariantRepository.deleteById(variantId);
            return ResponseEntity.noContent().build();

        } catch (Exception ex) {
            log.error("Errore eliminazione variante id={}", variantId, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    private void addLookupTables(Model model) {
        model.addAttribute("groups", groupRepository.findAllByOrderByCodeAsc());
        model.addAttribute("subgroups", subgroupRepository.findAllByOrderByCodeAsc());
        model.addAttribute("brands", brandRepository.findAllByOrderByCodeAsc());
        model.addAttribute("families", familyRepository.findAllByOrderByCodeAsc());
        model.addAttribute("subfamilies", subfamilyRepository.findAllByOrderByCodeAsc());
    }

    private void addProductLookupNames(Model model, Product product) {
        model.addAttribute("productGroupName", resolveLookupName(groupRepository, product.getGroupId()));
        model.addAttribute("productSubgroupName", resolveLookupName(subgroupRepository, product.getSubgroupId()));
        model.addAttribute("productBrandName", resolveLookupName(brandRepository, product.getBrandId()));
        model.addAttribute("productFamilyName", resolveLookupName(familyRepository, product.getFamilyId()));
        model.addAttribute("productSubfamilyName", resolveLookupName(subfamilyRepository, product.getSubfamilyId()));
    }

    private String resolveLookupName(ImageLookupRepository<? extends ImageLookupEntity> repository, Long id) {
        if (id == null) {
            return null;
        }

        return repository.findById(id)
                .map(this::formatLookupName)
                .orElse(null);
    }

    private String formatLookupName(ImageLookupEntity item) {
        if (item == null) {
            return null;
        }

        String descri = safe(item.getDescri());
        if (descri != null) {
            return descri;
        }

        return safe(item.getCode());
    }

    private static String safe(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public record ProductVariantRow(
            Long id,
            String variantCode,

            Long colorId,
            String colorCode,
            String colorName,

            Long sizeId,
            String sizeCode,
            String sizeName,

            BigDecimal price,
            Integer stockQty,
            boolean enabled,
            String mainImage
    ) {}

    @GetMapping
    public String listProducts(@RequestParam(value = "categoryId", required = false) Long categoryId,
                               @RequestParam(value = "q", required = false) String text,
                               Model model) {

        List<ProductCategory> categories = productService.findEnabledCategories();
        List<Product> products;

        ProductCategory selectedCategory = null;
        if (categoryId != null) {
            selectedCategory = productService.findCategoryById(categoryId).orElse(null);
        }

        if (selectedCategory != null || (text != null && !text.isBlank())) {
            products = productService.search(selectedCategory, text);
        } else {
            products = productService.findAll();
        }

        model.addAttribute("products", products);
        model.addAttribute("categories", categories);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("searchText", text);

        return "admin/products/product-list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        Product product = new Product();
        List<ProductCategory> categories = productService.findEnabledCategories();

        model.addAttribute("product", product);
        model.addAttribute("categories", categories);
        model.addAttribute("pageTitle", "Nuovo prodotto");

        model.addAttribute("images", Collections.emptyList());
        model.addAttribute("variants", Collections.emptyList());

        model.addAttribute("colors", colorOptionService.findAll());
        model.addAttribute("sizes", sizeOptionService.findAll());
        addLookupTables(model);

        return "admin/products/product-form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id,
                               Model model,
                               RedirectAttributes ra) {

        return productService.findWithImagesById(id)
                .map(p -> {
                    List<ProductCategory> categories = productService.findEnabledCategories();

                    model.addAttribute("product", p);
                    model.addAttribute("categories", categories);
                    model.addAttribute("pageTitle", "Modifica prodotto (ID: " + id + ")");

                    model.addAttribute("images", p.getImages() != null ? p.getImages() : Collections.emptyList());
                    model.addAttribute("variants", productVariantService.findByProductId(id));

                    model.addAttribute("colors", colorOptionService.findAll());
                    model.addAttribute("sizes", sizeOptionService.findAll());
                    addLookupTables(model);

                    return "admin/products/product-form";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("error", "Prodotto non trovato.");
                    return "redirect:/admin/products";
                });
    }

    @GetMapping("/view/{id}")
    public String viewProduct(@PathVariable("id") Long id,
                              @RequestParam(value = "categoryId", required = false) Long categoryId,
                              @RequestParam(value = "q", required = false) String text,
                              Model model,
                              RedirectAttributes ra) {

        Product product = productService.findWithImagesById(id).orElse(null);
        if (product == null) {
            ra.addFlashAttribute("error", "Prodotto non trovato.");
            return "redirect:/admin/products";
        }

        ProductCategory selectedCategory = null;
        if (categoryId != null) {
            selectedCategory = productService.findCategoryById(categoryId).orElse(null);
        }

        List<Product> products;
        if (selectedCategory != null || (text != null && !text.isBlank())) {
            products = productService.search(selectedCategory, text);
        } else {
            products = productService.findAll();
        }

        Long prevId = null;
        Long nextId = null;

        if (products != null && !products.isEmpty()) {
            int idx = -1;
            for (int i = 0; i < products.size(); i++) {
                Product p = products.get(i);
                if (p != null && p.getId() != null && p.getId().equals(id)) {
                    idx = i;
                    break;
                }
            }

            if (idx >= 0) {
                if (idx > 0) prevId = products.get(idx - 1).getId();
                if (idx < products.size() - 1) nextId = products.get(idx + 1).getId();
            }
        }

        model.addAttribute("product", product);
        model.addAttribute("pageTitle", "Dettaglio prodotto (ID: " + id + ")");
        model.addAttribute("images", product.getImages() != null ? product.getImages() : Collections.emptyList());
        addProductLookupNames(model, product);

        model.addAttribute("prevId", prevId);
        model.addAttribute("nextId", nextId);

        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("searchText", text);

        return "admin/products/product-view";
    }

    @PostMapping("/save")
    public String saveProduct(@Valid @ModelAttribute("product") Product product,
                              BindingResult bindingResult,
                              @RequestParam(value = "categoryId", required = false) Long categoryId,

                              @RequestParam(value = "mainImageFile", required = false) MultipartFile mainImageFile,
                              @RequestParam(value = "generateMainWebmFromImage", required = false, defaultValue = "false") boolean generateMainWebmFromImage,
                              @RequestParam(value = "deleteMainImage", required = false, defaultValue = "false") boolean deleteMainImage,

                              @RequestParam(value = "extraImageFiles", required = false) MultipartFile[] extraImageFiles,

                              @RequestParam(value = "imagesToDelete", required = false) List<Long> imagesToDelete,
                              @RequestParam(value = "replaceImages", required = false) MultipartFile[] replaceImages,
                              @RequestParam(value = "replaceImageIds", required = false) List<Long> replaceImageIds,

                              @RequestParam(value = "variantsRequested", required = false) Boolean variantsRequested,

                              @RequestParam(value = "variantCodes", required = false) List<String> variantCodes,
                              @RequestParam(value = "variantColorIds", required = false) List<Long> variantColorIds,
                              @RequestParam(value = "variantSizeIds", required = false) List<Long> variantSizeIds,

                              @RequestParam(value = "variantCodes[]", required = false) List<String> variantCodesArr,
                              @RequestParam(value = "variantColorIds[]", required = false) List<Long> variantColorIdsArr,
                              @RequestParam(value = "variantSizeIds[]", required = false) List<Long> variantSizeIdsArr,

                              Model model,
                              RedirectAttributes ra) {

        if (bindingResult.hasErrors()) {
            List<ProductCategory> categories = productService.findEnabledCategories();
            model.addAttribute("categories", categories);
            model.addAttribute("pageTitle",
                    product.getId() == null ? "Nuovo prodotto" : "Modifica prodotto (ID: " + product.getId() + ")");

            if (product.getId() != null) {
                productService.findWithImagesById(product.getId()).ifPresent(p -> {
                    model.addAttribute("images", p.getImages() != null ? p.getImages() : Collections.emptyList());
                    model.addAttribute("variants", productVariantService.findByProductId(product.getId()));
                });
            } else {
                model.addAttribute("images", Collections.emptyList());
                model.addAttribute("variants", Collections.emptyList());
            }

            model.addAttribute("colors", colorOptionService.findAll());
            model.addAttribute("sizes", sizeOptionService.findAll());
            addLookupTables(model);

            return "admin/products/product-form";
        }

        if (categoryId != null) {
            productService.findCategoryById(categoryId).ifPresent(product::setCategory);
        } else {
            product.setCategory(null);
        }

        Product toSave;
        Product existing = null;

        if (product.getId() != null) {
            existing = productService.findWithImagesById(product.getId()).orElse(null);
        }

        if (existing != null) {
            existing.setCategory(product.getCategory());

            existing.setCodpro(product.getCodpro());
            existing.setName(product.getName());
            existing.setDescription(product.getDescription());
            existing.setPrice(product.getPrice());
            existing.setStockQty(product.getStockQty()); // ✅ FIX: aggiorna anche lo stock del padre
            existing.setGroupId(product.getGroupId());
            existing.setSubgroupId(product.getSubgroupId());
            existing.setBrandId(product.getBrandId());
            existing.setFamilyId(product.getFamilyId());
            existing.setSubfamilyId(product.getSubfamilyId());
            existing.setMaterials(product.getMaterials());
            existing.setShippingInfo(product.getShippingInfo());
            existing.setMainMediaType(product.getMainMediaType());
            existing.setMainVideoPoster(product.getMainVideoPoster());

            toSave = existing;
        } else {
            toSave = product;
        }

        if (mainImageFile != null && !mainImageFile.isEmpty()) {
            try {
                String previousMainMedia = toSave.getImage();
                String previousMainMediaType = normalizeMediaTypeValue(toSave.getMainMediaType());
                String previousPoster = toSave.getMainVideoPoster();

                String mediaType = resolveMediaType(mainImageFile);
                String savedPath = storeFile(mainImageFile);

                if (generateMainWebmFromImage && videoGenerationService.isImageFile(mainImageFile)) {
                    try {
                        Path savedImagePath = resolveStoredProductUploadPath(savedPath);
                        Path generatedWebmPath = videoGenerationService.generateWebmFromImage(savedImagePath, getProductsUploadBasePath());
                        mediaType = Product.MAIN_MEDIA_TYPE_VIDEO;
                        toSave.setImage(toProductUploadUrl(generatedWebmPath));
                        toSave.setMainMediaType(Product.MAIN_MEDIA_TYPE_VIDEO);
                        toSave.setMainVideoPoster(savedPath);
                    } catch (RuntimeException generationException) {
                        log.warn("Generazione WebM non riuscita per il prodotto. Il media principale resta immagine: {}",
                                generationException.getMessage());
                        ra.addFlashAttribute("warning", "Prodotto salvato come immagine. Generazione WEBM non riuscita: "
                                + generationException.getMessage());
                        mediaType = Product.MAIN_MEDIA_TYPE_IMAGE;
                        toSave.setImage(savedPath);
                        toSave.setMainMediaType(Product.MAIN_MEDIA_TYPE_IMAGE);
                        toSave.setMainVideoPoster(null);
                    }
                } else {
                    String posterCandidate = null;

                    if (Product.MAIN_MEDIA_TYPE_VIDEO.equalsIgnoreCase(mediaType)) {
                        posterCandidate = findPosterCandidate(toSave, previousMainMedia, previousMainMediaType, previousPoster);
                    }

                    toSave.setImage(savedPath);
                    toSave.setMainMediaType(mediaType);

                    if (Product.MAIN_MEDIA_TYPE_VIDEO.equalsIgnoreCase(mediaType)) {
                        toSave.setMainVideoPoster(posterCandidate);
                    } else {
                        toSave.setMainVideoPoster(null);
                    }
                }

                if (previousMainMedia != null && !previousMainMedia.isBlank()) {
                    boolean keepPreviousAsPoster = Product.MAIN_MEDIA_TYPE_VIDEO.equalsIgnoreCase(mediaType)
                            && previousMainMedia.equals(toSave.getMainVideoPoster());

                    if (!keepPreviousAsPoster) {
                        deleteStoredFile(previousMainMedia);
                    }
                }

            } catch (Exception e) {
                log.error("Errore salvataggio/generazione media principale", e);
                ra.addFlashAttribute("error", "Errore durante il salvataggio o la generazione WebM del media principale: " + e.getMessage());
                return "redirect:/admin/products";
            }
        } else if (deleteMainImage) {
            if (toSave.getImage() != null && !toSave.getImage().isBlank()) {
                deleteStoredFile(toSave.getImage());
            }

            String poster = toSave.getMainVideoPoster();
            if (poster != null && !poster.isBlank() && !poster.equals(toSave.getImage())) {
                deleteStoredFile(poster);
            }

            toSave.setImage(null);
            toSave.setMainMediaType(Product.MAIN_MEDIA_TYPE_IMAGE);
            toSave.setMainVideoPoster(null);
        }

        if (existing != null) {
            Set<Long> replacedIds = new HashSet<>();

            if (replaceImages != null && replaceImageIds != null) {
                int len = Math.min(replaceImages.length, replaceImageIds.size());

                for (int i = 0; i < len; i++) {
                    MultipartFile file = replaceImages[i];
                    Long imageId = replaceImageIds.get(i);

                    if (file == null || file.isEmpty() || imageId == null) continue;

                    for (ProductImage img : existing.getImages()) {
                        if (img.getId() != null && img.getId().equals(imageId)) {
                            deleteStoredFile(img.getImageUrl());

                            try {
                                String newPath = storeFile(file);
                                img.setImageUrl(newPath);
                                img.setMediaType(resolveMediaType(file));
                                img.setPosterUrl(buildPosterUrl(existing, resolveMediaType(file)));
                                replacedIds.add(imageId);
                            } catch (IOException e) {
                                log.error("Errore sostituzione immagine id={}", imageId, e);
                                ra.addFlashAttribute("error", "Errore durante la sostituzione di un'immagine.");
                            }
                            break;
                        }
                    }
                }
            }

            if (imagesToDelete != null && !imagesToDelete.isEmpty()
                    && existing.getImages() != null && !existing.getImages().isEmpty()) {

                List<ProductImage> toRemove = new ArrayList<>();

                for (ProductImage img : existing.getImages()) {
                    if (img.getId() == null) continue;

                    Long imgId = img.getId();
                    if (replacedIds.contains(imgId)) continue;

                    if (imagesToDelete.contains(imgId)) {
                        deleteStoredFile(img.getImageUrl());
                        toRemove.add(img);
                    }
                }

                for (ProductImage img : toRemove) {
                    existing.removeImage(img);
                }
            }
        }

        Product saved = productService.save(toSave);

        if (extraImageFiles != null) {
            int maxSort = saved.getImages() != null ? saved.getImages().size() : 0;

            for (MultipartFile file : extraImageFiles) {
                if (file == null || file.isEmpty()) continue;

                try {
                    String savedPath = storeFile(file);

                    ProductImage img = new ProductImage();
                    img.setProduct(saved);
                    img.setImageUrl(savedPath);
                    img.setAltText(saved.getName());
                    img.setSortOrder(++maxSort);
                    img.setMediaType(resolveMediaType(file));
                    img.setPosterUrl(buildPosterUrl(saved, resolveMediaType(file)));

                    saved.addImage(img);

                } catch (IOException e) {
                    log.error("Errore salvataggio immagine extra", e);
                    ra.addFlashAttribute("error", "Errore durante il salvataggio di una delle immagini extra.");
                }
            }

            saved = productService.save(saved);
        }

        List<String> codes = (variantCodes != null && !variantCodes.isEmpty()) ? variantCodes : variantCodesArr;
        List<Long> colorIds = (variantColorIds != null && !variantColorIds.isEmpty()) ? variantColorIds : variantColorIdsArr;
        List<Long> sizeIds = (variantSizeIds != null && !variantSizeIds.isEmpty()) ? variantSizeIds : variantSizeIdsArr;

        int createdVariants = 0;

        boolean wantsVariants = Boolean.TRUE.equals(variantsRequested)
                || (codes != null && !codes.isEmpty());

        if (wantsVariants) {
            Set<String> existingVariantCodesBefore = productVariantService.findByProductId(saved.getId()).stream()
                    .map(ProductVariant::getVariantCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            log.debug("POST varianti: variantsRequested={}, codes={}, colorIds={}, sizeIds={}",
                    variantsRequested,
                    (codes == null ? null : codes.size()),
                    (colorIds == null ? null : colorIds.size()),
                    (sizeIds == null ? null : sizeIds.size()));

            createdVariants = productVariantService.createMissingVariants(saved, codes, colorIds, sizeIds);
            log.debug("createMissingVariants -> created={}", createdVariants);

            if (createdVariants > 0) {
                initializeNewVariantsFromParent(saved, existingVariantCodesBefore);
            }
        } else {
            log.debug("POST varianti: nessun parametro varianti ricevuto (non creo nulla).");
        }

        String msg = "Prodotto salvato correttamente (ID: " + saved.getId() + ").";
        if (createdVariants > 0) {
            msg += " Varianti create: " + createdVariants + ".";
        } else if (wantsVariants) {
            msg += " Varianti non create (controlla log DEBUG: parametri non ricevuti o combinazioni non valide).";
        }

        ra.addFlashAttribute("success", msg);
        return "redirect:/admin/products";
    }

    /**
     * ✅ Imposta prezzo e stockQty del prodotto padre SOLO sulle varianti appena create,
     * senza sovrascrivere eventuali varianti già esistenti.
     */
    private void initializeNewVariantsFromParent(Product savedProduct, Set<String> existingVariantCodesBefore) {
        try {
            List<ProductVariant> currentVariants = productVariantService.findByProductId(savedProduct.getId());
            if (currentVariants == null || currentVariants.isEmpty()) {
                return;
            }

            BigDecimal parentPrice = savedProduct.getPrice();
            Integer parentStockQty = savedProduct.getStockQty();

            int updated = 0;

            for (ProductVariant variant : currentVariants) {
                String code = variant.getVariantCode();
                boolean isNew = code != null && !existingVariantCodesBefore.contains(code);

                if (!isNew) {
                    continue;
                }

                variant.setPrice(parentPrice);
                variant.setStockQty(parentStockQty);

                productVariantRepository.save(variant);
                updated++;
            }

            log.debug("Varianti nuove inizializzate da prodotto padre: {}", updated);

        } catch (Exception ex) {
            log.error("Errore inizializzazione prezzo/stock varianti da padre productId={}", savedProduct.getId(), ex);
        }
    }

    @PostMapping("/delete")
    public String deleteProductPost(@RequestParam("id") Long id,
                                    RedirectAttributes ra) {
        return deleteProduct(id, ra);
    }

    @PostMapping("/delete/{id}")
    public String deleteProduct(@PathVariable("id") Long id,
                                RedirectAttributes ra) {

        productService.findById(id).ifPresentOrElse(p -> {
            productService.deleteById(id);
            ra.addFlashAttribute("success", "Prodotto eliminato correttamente (ID: " + id + ").");
        }, () -> ra.addFlashAttribute("error", "Prodotto non trovato."));

        return "redirect:/admin/products";
    }

    private String storeFile(MultipartFile file) throws IOException {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "image";
        }

        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex);
        }

        String randomName = UUID.randomUUID().toString().replaceAll("-", "");
        String finalName = randomName + extension;

        Path uploadBase = Paths.get(productsUploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadBase);

        Path target = uploadBase.resolve(finalName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        String relativePath = productsUploadDir.replace("\\", "/");
        if (!relativePath.startsWith("/")) relativePath = "/" + relativePath;
        if (!relativePath.endsWith("/")) relativePath = relativePath + "/";

        return relativePath + finalName;
    }

    private Path getProductsUploadBasePath() {
        return Paths.get(productsUploadDir).toAbsolutePath().normalize();
    }

    private Path resolveStoredProductUploadPath(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) {
            throw new IllegalArgumentException("Percorso prodotto non valido.");
        }

        String fileName = storedUrl;
        int lastSlash = storedUrl.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < storedUrl.length() - 1) {
            fileName = storedUrl.substring(lastSlash + 1);
        }

        Path uploadBase = getProductsUploadBasePath();
        Path target = uploadBase.resolve(fileName).normalize();
        if (!target.startsWith(uploadBase)) {
            throw new IllegalArgumentException("Percorso prodotto non sicuro.");
        }

        return target;
    }

    private String toProductUploadUrl(Path path) {
        if (path == null || path.getFileName() == null) {
            throw new IllegalArgumentException("Percorso WebM prodotto non valido.");
        }

        String relativePath = productsUploadDir.replace("\\", "/");
        if (!relativePath.startsWith("/")) relativePath = "/" + relativePath;
        if (!relativePath.endsWith("/")) relativePath = relativePath + "/";

        return relativePath + path.getFileName();
    }

    private String resolveMediaType(MultipartFile file) {
        if (file == null) {
            return ProductImage.MEDIA_TYPE_IMAGE;
        }

        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("video/")) {
            return ProductImage.MEDIA_TYPE_VIDEO;
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".ogg") || lower.endsWith(".mov")) {
                return ProductImage.MEDIA_TYPE_VIDEO;
            }
        }

        return ProductImage.MEDIA_TYPE_IMAGE;
    }

    private String buildPosterUrl(Product product, String mediaType) {
        if (!ProductImage.MEDIA_TYPE_VIDEO.equalsIgnoreCase(mediaType)) {
            return null;
        }

        if (product == null) {
            return null;
        }

        if (product.getMainVideoPoster() != null && !product.getMainVideoPoster().isBlank()) {
            return product.getMainVideoPoster();
        }

        if (Product.MAIN_MEDIA_TYPE_IMAGE.equalsIgnoreCase(normalizeMediaTypeValue(product.getMainMediaType()))
                && product.getImage() != null && !product.getImage().isBlank()) {
            return product.getImage();
        }

        if (product.getImages() != null) {
            return product.getImages().stream()
                    .filter(Objects::nonNull)
                    .filter(ProductImage::isImage)
                    .map(ProductImage::getImageUrl)
                    .filter(Objects::nonNull)
                    .filter(url -> !url.isBlank())
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    private String findPosterCandidate(Product product,
                                       String previousMainMedia,
                                       String previousMainMediaType,
                                       String previousPoster) {
        if (previousPoster != null && !previousPoster.isBlank()) {
            return previousPoster;
        }

        if (Product.MAIN_MEDIA_TYPE_IMAGE.equalsIgnoreCase(normalizeMediaTypeValue(previousMainMediaType))
                && previousMainMedia != null && !previousMainMedia.isBlank()) {
            return previousMainMedia;
        }

        if (product != null && product.getImages() != null) {
            return product.getImages().stream()
                    .filter(Objects::nonNull)
                    .filter(ProductImage::isImage)
                    .map(ProductImage::getImageUrl)
                    .filter(Objects::nonNull)
                    .filter(url -> !url.isBlank())
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    private String normalizeMediaTypeValue(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return Product.MAIN_MEDIA_TYPE_IMAGE;
        }

        return Product.MAIN_MEDIA_TYPE_VIDEO.equalsIgnoreCase(mediaType)
                ? Product.MAIN_MEDIA_TYPE_VIDEO
                : Product.MAIN_MEDIA_TYPE_IMAGE;
    }

    private void deleteStoredFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;

        String lower = imageUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return;
        }

        try {
            String fileName = imageUrl;
            int lastSlash = imageUrl.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < imageUrl.length() - 1) {
                fileName = imageUrl.substring(lastSlash + 1);
            }

            Path uploadBase = Paths.get(productsUploadDir).toAbsolutePath().normalize();
            Path target = uploadBase.resolve(fileName).normalize();

            if (!target.startsWith(uploadBase)) return;

            Files.deleteIfExists(target);
        } catch (Exception ignored) {
            // best effort
        }
    }
}