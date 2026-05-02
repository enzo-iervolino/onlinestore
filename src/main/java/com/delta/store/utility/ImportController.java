package com.delta.store.utility;

import com.delta.store.product.dto.ProductDTO;
import com.delta.store.product.service.ProductService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
public class ImportController {

    private static final String SESSION_PENDING_PRODUCTS = "PENDING_PRODUCT_IMPORT";
    private static final String SESSION_PENDING_FILENAME = "PENDING_PRODUCT_IMPORT_FILENAME";

    private final GenericImportService genericImportService;
    private final ProductService productService;

    public ImportController(GenericImportService genericImportService,
                            ProductService productService) {
        this.genericImportService = genericImportService;
        this.productService = productService;
    }

    @GetMapping("/admin/import")
    public String showImportForm(HttpSession session) {
        session.removeAttribute(SESSION_PENDING_PRODUCTS);
        session.removeAttribute(SESSION_PENDING_FILENAME);
        return "admin/import-form";
    }

    @PostMapping("/admin/import")
    public String previewImport(@RequestParam("file") MultipartFile file,
                                Model model,
                                HttpSession session) {
        try {
            if (file == null || file.isEmpty()) {
                model.addAttribute("error", "Seleziona un file da importare.");
                return "admin/import-form";
            }

            ImportResult result = genericImportService.previewFile(file);

            session.setAttribute(SESSION_PENDING_PRODUCTS, result.getImportedItems());
            session.setAttribute(SESSION_PENDING_FILENAME, file.getOriginalFilename());

            model.addAttribute("result", result);
            model.addAttribute("filename", file.getOriginalFilename());
            model.addAttribute("previewMode", true);
            model.addAttribute("confirmedMode", false);

            return "admin/import-result";

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "admin/import-form";
        }
    }

    @PostMapping("/admin/import/confirm")
    @SuppressWarnings("unchecked")
    public String confirmImport(Model model, HttpSession session) {
        Object pendingProductsObject = session.getAttribute(SESSION_PENDING_PRODUCTS);
        Object pendingFilenameObject = session.getAttribute(SESSION_PENDING_FILENAME);

        if (!(pendingProductsObject instanceof List<?>)) {
            model.addAttribute("error", "Nessuna importazione da confermare. Carica prima un file.");
            return "admin/import-form";
        }

        List<ProductDTO> pendingProducts = (List<ProductDTO>) pendingProductsObject;
        String filename = pendingFilenameObject != null ? pendingFilenameObject.toString() : "file importato";

        if (pendingProducts.isEmpty()) {
            model.addAttribute("error", "Non ci sono prodotti validi da importare.");
            return "admin/import-form";
        }

        ImportResult result = new ImportResult();

        int rowNumber = 0;

        for (ProductDTO dto : pendingProducts) {
            rowNumber++;
            result.incrementTotalRows();

            try {
                productService.importProduct(dto);

                result.addImportedItem(dto);
                result.incrementImportedRows();
            } catch (Exception ex) {
                result.incrementSkippedRows();
                result.addError("Prodotto " + rowNumber + " [" + safeCodpro(dto) + "]: " + ex.getMessage());
            }
        }

        session.removeAttribute(SESSION_PENDING_PRODUCTS);
        session.removeAttribute(SESSION_PENDING_FILENAME);

        model.addAttribute("result", result);
        model.addAttribute("filename", filename);
        model.addAttribute("previewMode", false);
        model.addAttribute("confirmedMode", true);

        return "admin/import-result";
    }

    @GetMapping("/admin/import-result")
    public String redirectImportResult() {
        return "redirect:/admin/import";
    }

    private String safeCodpro(ProductDTO dto) {
        if (dto == null || dto.getCodpro() == null || dto.getCodpro().isBlank()) {
            return "senza codice";
        }
        return dto.getCodpro();
    }
}