package com.delta.store.web.admin;

import com.delta.store.product.model.ProductVariant;
import com.delta.store.product.service.ColorOptionService;
import com.delta.store.product.service.ProductCodeService;
import com.delta.store.product.service.ProductVariantService;
import com.delta.store.product.service.SizeOptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/variants")
public class AdminVariantController {

    private static final Logger log = LoggerFactory.getLogger(AdminVariantController.class);

    private final ProductVariantService productVariantService;
    private final ProductCodeService productCodeService;
    private final ColorOptionService colorOptionService;
    private final SizeOptionService sizeOptionService;

    public AdminVariantController(ProductVariantService productVariantService,
                                  ProductCodeService productCodeService,
                                  ColorOptionService colorOptionService,
                                  SizeOptionService sizeOptionService) {
        this.productVariantService = productVariantService;
        this.productCodeService = productCodeService;
        this.colorOptionService = colorOptionService;
        this.sizeOptionService = sizeOptionService;
    }

    @GetMapping("/new")
    public String newVariantPage(@RequestParam Long productId, Model model) {
        model.addAttribute("productId", productId);
        model.addAttribute("colors", colorOptionService.findAll());
        model.addAttribute("sizes", sizeOptionService.findAll());
        if (!model.containsAttribute("additionalCodesText")) {
            model.addAttribute("additionalCodesText", "");
        }
        return "admin/variants/new";
    }

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String createVariant(@RequestParam Long productId,
                                @RequestParam Long colorId,
                                @RequestParam Long sizeId,
                                @RequestParam String variantCode,
                                @RequestParam(required = false) String additionalCodes,
                                @RequestParam(required = false) BigDecimal price,
                                @RequestParam(required = false) Integer stockQty,
                                @RequestParam(defaultValue = "false") boolean enabled,
                                @RequestParam(required = false) MultipartFile mainImageFile,
                                @RequestParam(defaultValue = "false") boolean generateMainWebmFromImage,
                                @RequestParam(required = false, name = "galleryFiles") List<MultipartFile> galleryFiles,
                                RedirectAttributes redirectAttributes) {

        try {
            productCodeService.validateVariantCodes(null, variantCode, null);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("additionalCodesText", value(additionalCodes));
            return "redirect:/admin/variants/new?productId=" + productId;
        } catch (RuntimeException infrastructureError) {
            log.warn(
                    "Verifica preventiva del codice variante non disponibile; procedo con il salvataggio standard: {}",
                    rootCauseMessage(infrastructureError)
            );
        }

        ProductVariant created;
        try {
            created = productVariantService.createVariant(
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
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", rootCauseMessage(ex));
            redirectAttributes.addFlashAttribute("additionalCodesText", value(additionalCodes));
            return "redirect:/admin/variants/new?productId=" + productId;
        }

        try {
            int savedCodes = productCodeService.replaceVariantCodes(created, additionalCodes);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Variante creata con successo. Codici alternativi salvati: " + savedCodes + "."
            );
        } catch (Exception ex) {
            log.error("Variante ID {} creata, ma salvataggio codici non riuscito", created.getId(), ex);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "La variante è stata creata, ma i codici alternativi NON sono stati salvati: "
                            + rootCauseMessage(ex)
            );
            redirectAttributes.addFlashAttribute("additionalCodesText", value(additionalCodes));
        }

        return "redirect:/admin/variants/" + created.getId() + "/edit";
    }

    @GetMapping("/{variantId}/edit")
    public String editVariantPage(@PathVariable Long variantId, Model model) {
        ProductVariant variant = productVariantService.findByIdForEdit(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variante non trovata: " + variantId));

        model.addAttribute("variant", variant);
        if (!model.containsAttribute("additionalCodesText")) {
            model.addAttribute("additionalCodesText", productCodeService.formatVariantCodes(variantId));
        }
        model.addAttribute("additionalCodesSaved", productCodeService.findVariantCodes(variantId));
        return "admin/variants/edit";
    }

    @PostMapping(value = "/{variantId}/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String updateVariant(@PathVariable Long variantId,
                                @RequestParam String variantCode,
                                @RequestParam(required = false) String additionalCodes,
                                @RequestParam(required = false) BigDecimal price,
                                @RequestParam(required = false) Integer stockQty,
                                @RequestParam(defaultValue = "false") boolean enabled,
                                @RequestParam(required = false) MultipartFile mainImageFile,
                                @RequestParam(defaultValue = "false") boolean generateMainWebmFromImage,
                                @RequestParam(required = false, name = "galleryFiles") List<MultipartFile> galleryFiles,
                                @RequestParam(required = false, name = "removeImageIds") List<Long> removeImageIds,
                                RedirectAttributes redirectAttributes) {

        try {
            productCodeService.validateVariantCodes(variantId, variantCode, null);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("additionalCodesText", value(additionalCodes));
            return "redirect:/admin/variants/" + variantId + "/edit";
        } catch (RuntimeException infrastructureError) {
            log.warn(
                    "Verifica preventiva del codice variante non disponibile; procedo con il salvataggio standard: {}",
                    rootCauseMessage(infrastructureError)
            );
        }

        ProductVariant updated;
        try {
            updated = productVariantService.updateVariant(
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
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", rootCauseMessage(ex));
            redirectAttributes.addFlashAttribute("additionalCodesText", value(additionalCodes));
            return "redirect:/admin/variants/" + variantId + "/edit";
        }

        try {
            int savedCodes = productCodeService.replaceVariantCodes(updated, additionalCodes);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Variante aggiornata con successo. Codici alternativi salvati: " + savedCodes + "."
            );
        } catch (Exception ex) {
            log.error("Variante ID {} aggiornata, ma salvataggio codici non riuscito", variantId, ex);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "La variante è stata aggiornata, ma i codici alternativi NON sono stati salvati: "
                            + rootCauseMessage(ex)
            );
            redirectAttributes.addFlashAttribute("additionalCodesText", value(additionalCodes));
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

    private Map<String, Object> toRow(ProductVariant variant) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", variant.getId());
        row.put("variantCode", value(variant.getVariantCode()));
        row.put("price", variant.getPrice());
        row.put("stockQty", variant.getStockQty());
        row.put("enabled", variant.isEnabled());
        row.put("mainImage", value(variant.getMainImage()));
        row.put("mainMediaType", value(variant.getMainMediaType()));
        row.put("mainVideoPoster", value(variant.getMainVideoPoster()));
        row.put("colorCode", variant.getColor() != null ? value(variant.getColor().getCode()) : "");
        row.put("colorName", variant.getColor() != null ? value(variant.getColor().getNameIt()) : "");
        row.put("sizeCode", variant.getSize() != null ? value(variant.getSize().getCode()) : "");
        row.put("sizeName", variant.getSize() != null ? value(variant.getSize().getNameIt()) : "");
        return row;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String rootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return "errore non specificato";
        }

        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }

        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }
}
