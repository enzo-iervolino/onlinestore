package com.delta.store.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductCategory;
import com.delta.store.product.service.ProductService;

@Controller
public class PageController {

    private final ProductService productService;

    public PageController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/contatti")
    public String contatti() {
        return "contatti";
    }

    @GetMapping("/catalogo")
    public String catalogo(@RequestParam(name = "category", required = false) String categoryCode,
                           Model model) {

        List<ProductCategory> categories = productService.findEnabledCategories();

        ProductCategory selectedCategory = null;
        if (categoryCode != null && !categoryCode.isBlank()) {
            for (ProductCategory category : categories) {
                if (category.getCode() != null
                        && category.getCode().equalsIgnoreCase(categoryCode.trim())) {
                    selectedCategory = category;
                    break;
                }
            }
        }

        List<Product> products = productService.search(selectedCategory, null);

        model.addAttribute("categories", categories);
        model.addAttribute("products", products);
        model.addAttribute("selectedCategoryCode",
                selectedCategory != null ? selectedCategory.getCode() : null);
        model.addAttribute("selectedCategoryName",
                selectedCategory != null ? selectedCategory.getName() : null);

        return "catalogo";
    }

    @GetMapping("/admin/dashboard")
    public String dashboard() {
        return "admin/dashboard";
    }

    /**
     * Login è in MODALE: /login serve solo a reindirizzare alla home con flag.
     */
    @GetMapping("/login")
    public String loginRedirect(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout
    ) {
        if (error != null) return "redirect:/?login&error";
        if (logout != null) return "redirect:/?logout";
        return "redirect:/?login";
    }
}