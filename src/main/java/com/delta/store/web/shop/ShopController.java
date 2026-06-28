package com.delta.store.web.shop;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.delta.store.config.ShopRoutingProperties;
import com.delta.store.product.dto.ShopProductDetailDTO;
import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductCategory;
import com.delta.store.product.service.ProductService;
import com.delta.store.product.service.ShopProductDetailService;

@Controller
public class ShopController {

    private final ProductService productService;
    private final ShopProductDetailService shopProductDetailService;
    private final ShopRoutingProperties shopRoutingProperties;

    public ShopController(ProductService productService,
                          ShopProductDetailService shopProductDetailService,
                          ShopRoutingProperties shopRoutingProperties) {
        this.productService = productService;
        this.shopProductDetailService = shopProductDetailService;
        this.shopRoutingProperties = shopRoutingProperties;
    }

    @GetMapping("/shop")
    public String shop(@RequestParam(name = "category", required = false) String categoryCode,
                       Model model) {
        loadCatalogData(categoryCode, model);
        return shopRoutingProperties.getPage("shop");
    }

    

    @GetMapping("/shop/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        ShopProductDetailDTO detail = shopProductDetailService.buildDetail(id);
        model.addAttribute("detail", detail);
        return shopRoutingProperties.getPage("detail-shop");
    }

    private void loadCatalogData(String categoryCode, Model model) {
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
    }
}