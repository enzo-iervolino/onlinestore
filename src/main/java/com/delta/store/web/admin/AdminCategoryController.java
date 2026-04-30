package com.delta.store.web.admin;

import com.delta.store.product.model.ProductCategory;
import com.delta.store.product.repository.ProductCategoryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    private final ProductCategoryRepository categoryRepository;

    public AdminCategoryController(ProductCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public String listCategories(Model model,
                                 @RequestParam(value = "keyword", required = false) String keyword) {

        var categories = categoryRepository.findAll()
                .stream()
                .sorted(Comparator
                        .comparing((ProductCategory c) -> c.getSortOrder() == null ? Integer.MAX_VALUE : c.getSortOrder())
                        .thenComparing(c -> c.getName() == null ? "" : c.getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (StringUtils.hasText(keyword)) {
            String q = keyword.trim().toLowerCase();
            categories = categories.stream()
                    .filter(c ->
                            contains(c.getCode(), q) ||
                            contains(c.getName(), q) ||
                            contains(c.getDescription(), q))
                    .toList();
        }

        model.addAttribute("categories", categories);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageTitle", "Lista categorie");

        return "admin/categories/list";
    }

    @GetMapping("/new")
    public String newCategoryForm(Model model) {
        ProductCategory category = new ProductCategory();
        category.setEnabled(true);

        model.addAttribute("category", category);
        model.addAttribute("pageTitle", "Nuova categoria");
        model.addAttribute("formAction", "/admin/categories/new");
        model.addAttribute("isEdit", false);

        return "admin/categories/form";
    }

    @PostMapping("/new")
    public String createCategory(@ModelAttribute("category") ProductCategory category,
                                 @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {

        normalize(category);

        if (!StringUtils.hasText(category.getCode())) {
            model.addAttribute("pageTitle", "Nuova categoria");
            model.addAttribute("formAction", "/admin/categories/new");
            model.addAttribute("isEdit", false);
            model.addAttribute("errorMessage", "Il codice categoria è obbligatorio.");
            return "admin/categories/form";
        }

        if (!StringUtils.hasText(category.getName())) {
            model.addAttribute("pageTitle", "Nuova categoria");
            model.addAttribute("formAction", "/admin/categories/new");
            model.addAttribute("isEdit", false);
            model.addAttribute("errorMessage", "Il nome categoria è obbligatorio.");
            return "admin/categories/form";
        }

        Optional<ProductCategory> existing = categoryRepository.findByCode(category.getCode());
        if (existing.isPresent()) {
            model.addAttribute("pageTitle", "Nuova categoria");
            model.addAttribute("formAction", "/admin/categories/new");
            model.addAttribute("isEdit", false);
            model.addAttribute("errorMessage", "Esiste già una categoria con questo codice.");
            return "admin/categories/form";
        }

        try {
            String imageUrl = saveImageIfPresent(imageFile);
            if (StringUtils.hasText(imageUrl)) {
                category.setImageUrl(imageUrl);
            }
        } catch (IOException e) {
            model.addAttribute("pageTitle", "Nuova categoria");
            model.addAttribute("formAction", "/admin/categories/new");
            model.addAttribute("isEdit", false);
            model.addAttribute("errorMessage", "Errore durante il caricamento dell'immagine.");
            return "admin/categories/form";
        }

        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("successMessage", "Categoria creata con successo.");

        return "redirect:/admin/categories";
    }

    @GetMapping("/{id}")
    public String viewCategory(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<ProductCategory> optionalCategory = categoryRepository.findById(id);

        if (optionalCategory.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Categoria non trovata.");
            return "redirect:/admin/categories";
        }

        model.addAttribute("category", optionalCategory.get());
        model.addAttribute("pageTitle", "Dettaglio categoria");

        return "admin/categories/view";
    }

    @GetMapping("/{id}/edit")
    public String editCategoryForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<ProductCategory> optionalCategory = categoryRepository.findById(id);

        if (optionalCategory.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Categoria non trovata.");
            return "redirect:/admin/categories";
        }

        model.addAttribute("category", optionalCategory.get());
        model.addAttribute("pageTitle", "Modifica categoria");
        model.addAttribute("formAction", "/admin/categories/" + id + "/edit");
        model.addAttribute("isEdit", true);

        return "admin/categories/form";
    }

    @PostMapping("/{id}/edit")
    public String updateCategory(@PathVariable Long id,
                                 @ModelAttribute("category") ProductCategory formCategory,
                                 @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {

        Optional<ProductCategory> optionalCategory = categoryRepository.findById(id);

        if (optionalCategory.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Categoria non trovata.");
            return "redirect:/admin/categories";
        }

        normalize(formCategory);

        if (!StringUtils.hasText(formCategory.getCode())) {
            model.addAttribute("pageTitle", "Modifica categoria");
            model.addAttribute("formAction", "/admin/categories/" + id + "/edit");
            model.addAttribute("isEdit", true);
            model.addAttribute("errorMessage", "Il codice categoria è obbligatorio.");
            return "admin/categories/form";
        }

        if (!StringUtils.hasText(formCategory.getName())) {
            model.addAttribute("pageTitle", "Modifica categoria");
            model.addAttribute("formAction", "/admin/categories/" + id + "/edit");
            model.addAttribute("isEdit", true);
            model.addAttribute("errorMessage", "Il nome categoria è obbligatorio.");
            return "admin/categories/form";
        }

        Optional<ProductCategory> existingByCode = categoryRepository.findByCode(formCategory.getCode());
        if (existingByCode.isPresent() && !existingByCode.get().getId().equals(id)) {
            model.addAttribute("pageTitle", "Modifica categoria");
            model.addAttribute("formAction", "/admin/categories/" + id + "/edit");
            model.addAttribute("isEdit", true);
            model.addAttribute("errorMessage", "Esiste già un'altra categoria con questo codice.");
            return "admin/categories/form";
        }

        ProductCategory category = optionalCategory.get();
        category.setCode(formCategory.getCode());
        category.setName(formCategory.getName());
        category.setDescription(formCategory.getDescription());
        category.setEnabled(formCategory.isEnabled());
        category.setSortOrder(formCategory.getSortOrder());

        try {
            String imageUrl = saveImageIfPresent(imageFile);
            if (StringUtils.hasText(imageUrl)) {
                category.setImageUrl(imageUrl);
            } else {
                category.setImageUrl(formCategory.getImageUrl());
            }
        } catch (IOException e) {
            model.addAttribute("pageTitle", "Modifica categoria");
            model.addAttribute("formAction", "/admin/categories/" + id + "/edit");
            model.addAttribute("isEdit", true);
            model.addAttribute("errorMessage", "Errore durante il caricamento dell'immagine.");
            return "admin/categories/form";
        }

        categoryRepository.save(category);
        redirectAttributes.addFlashAttribute("successMessage", "Categoria aggiornata con successo.");

        return "redirect:/admin/categories";
    }

    private String saveImageIfPresent(MultipartFile imageFile) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }

        String originalFilename = imageFile.getOriginalFilename();
        String extension = getFileExtension(originalFilename).toLowerCase();

        if (!extension.equals("jpg") && !extension.equals("jpeg") && !extension.equals("png") && !extension.equals("webp")) {
            throw new IOException("Formato file non supportato");
        }

        Path uploadDir = Paths.get("uploads/categories");
        Files.createDirectories(uploadDir);

        String newFilename = UUID.randomUUID() + "." + extension;
        Path filePath = uploadDir.resolve(newFilename);

        Files.copy(imageFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/categories/" + newFilename;
    }

    private String getFileExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private void normalize(ProductCategory category) {
        if (category.getCode() != null) {
            category.setCode(category.getCode().trim().toUpperCase());
        }
        if (category.getName() != null) {
            category.setName(category.getName().trim());
        }
        if (category.getDescription() != null) {
            category.setDescription(category.getDescription().trim());
        }
        if (category.getImageUrl() != null) {
            category.setImageUrl(category.getImageUrl().trim());
        }
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}