package com.delta.store.web.admin;

import com.delta.store.product.model.ProductVariant;
import com.delta.store.product.service.ColorOptionService;
import com.delta.store.product.service.ProductVariantService;
import com.delta.store.product.service.SizeOptionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/variants")
public class AdminVariantController {

    private final ProductVariantService productVariantService;
    private final ColorOptionService colorOptionService;
    private final SizeOptionService sizeOptionService;

    public AdminVariantController(ProductVariantService productVariantService,
                                  ColorOptionService colorOptionService,
                                  SizeOptionService sizeOptionService) {
        this.productVariantService = productVariantService;
        this.colorOptionService = colorOptionService;
        this.sizeOptionService = sizeOptionService;
    }

    @GetMapping("/new")
    public String newVariantPage(@RequestParam Long productId, Model model) {
        model.addAttribute("productId", productId);
        model.addAttribute("colors", colorOptionService.findAll());
        model.addAttribute("sizes", sizeOptionService.findAll());
        return "admin/variants/new";
    }

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String createVariant(@RequestParam Long productId,
                                @RequestParam Long colorId,
                                @RequestParam Long sizeId,
                                @RequestParam String variantCode,
                                @RequestParam(required = false) BigDecimal price,
                                @RequestParam(required = false) Integer stockQty,
                                @RequestParam(defaultValue = "false") boolean enabled,
                                @RequestParam(required = false) MultipartFile mainImageFile,
                                @RequestParam(defaultValue = "false") boolean generateMainWebmFromImage,
                                @RequestParam(required = false, name = "galleryFiles") List<MultipartFile> galleryFiles,
                                RedirectAttributes redirectAttributes) {

        try {
            ProductVariant created = productVariantService.createVariant(
                    productId,
                    colorId,
                    sizeId,
                    variantCode,
                    price,
                    stockQty,
                    enabled,
                    mainImageFile,
                    generateMainWebmFromImage,
                    galleryFiles
            );

            redirectAttributes.addFlashAttribute("successMessage", "Variante creata con successo.");
            return "redirect:/admin/variants/" + created.getId() + "/edit";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/variants/new?productId=" + productId;
        }
    }

    @GetMapping("/{variantId}/edit")
    public String editVariantPage(@PathVariable Long variantId, Model model) {
        ProductVariant variant = productVariantService.findByIdForEdit(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variante non trovata: " + variantId));

        model.addAttribute("variant", variant);
        return "admin/variants/edit";
    }

    @PostMapping(value = "/{variantId}/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String updateVariant(@PathVariable Long variantId,
                                @RequestParam String variantCode,
                                @RequestParam(required = false) BigDecimal price,
                                @RequestParam(required = false) Integer stockQty,
                                @RequestParam(defaultValue = "false") boolean enabled,
                                @RequestParam(required = false) MultipartFile mainImageFile,
                                @RequestParam(defaultValue = "false") boolean generateMainWebmFromImage,
                                @RequestParam(required = false, name = "galleryFiles") List<MultipartFile> galleryFiles,
                                @RequestParam(required = false, name = "removeImageIds") List<Long> removeImageIds,
                                RedirectAttributes redirectAttributes) {

        try {
            productVariantService.updateVariant(
                    variantId,
                    variantCode,
                    price,
                    stockQty,
                    enabled,
                    mainImageFile,
                    generateMainWebmFromImage,
                    galleryFiles,
                    removeImageIds
            );

            redirectAttributes.addFlashAttribute("successMessage", "Variante aggiornata con successo.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/variants/" + variantId + "/edit";
    }

    @ResponseBody
    @GetMapping("/{productId}/variants")
    public List<Map<String, Object>> getVariantsByProduct(@PathVariable Long productId) {
        return productVariantService.findByProductId(productId)
                .stream()
                .map(this::toRow)
                .toList();
    }

    @ResponseBody
    @DeleteMapping("/{variantId}")
    public ResponseEntity<Void> deleteVariant(@PathVariable Long variantId) {
        productVariantService.deleteVariant(variantId);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toRow(ProductVariant v) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", v.getId());
        row.put("variantCode", value(v.getVariantCode()));
        row.put("price", v.getPrice());
        row.put("stockQty", v.getStockQty());
        row.put("enabled", v.isEnabled());
        row.put("mainImage", value(v.getMainImage()));
        row.put("mainMediaType", value(v.getMainMediaType()));
        row.put("mainVideoPoster", value(v.getMainVideoPoster()));
        row.put("colorCode", v.getColor() != null ? value(v.getColor().getCode()) : "");
        row.put("colorName", v.getColor() != null ? value(v.getColor().getNameIt()) : "");
        row.put("sizeCode", v.getSize() != null ? value(v.getSize().getCode()) : "");
        row.put("sizeName", v.getSize() != null ? value(v.getSize().getNameIt()) : "");
        return row;
    }

    private String value(String s) {
        return s == null ? "" : s;
    }
}
