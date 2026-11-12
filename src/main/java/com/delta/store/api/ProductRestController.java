package com.delta.store.api;

import com.delta.store.product.dto.ProductCodeLookupDTO;
import com.delta.store.product.dto.ProductDTO;
import com.delta.store.product.mapper.ProductMapper;
import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductCategory;
import com.delta.store.product.service.ProductCodeService;
import com.delta.store.product.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductRestController {

    private final ProductService productService;
    private final ProductCodeService productCodeService;

    public ProductRestController(ProductService productService,
                                 ProductCodeService productCodeService) {
        this.productService = productService;
        this.productCodeService = productCodeService;
    }

    public ResponseEntity<List<ProductDTO>> findAll() {
        return ResponseEntity.ok(productService.findAllDto());
    }

    public ResponseEntity<ProductDTO> findById(@PathVariable Long id) {
        return productService.findWithImagesByIdDto(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/lookup")
    public ResponseEntity<ProductCodeLookupDTO> lookupByCode(@RequestParam String code) {
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return productCodeService.lookup(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductDTO>> search(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String text) {

        ProductCategory category = null;

        if (categoryId != null) {
            category = productService.findCategoryById(categoryId).orElse(null);
        }

        return ResponseEntity.ok(productService.searchDto(category, text));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ProductDTO dto) {

        if (dto == null) {
            return ResponseEntity.badRequest().body("Request body mancante");
        }

        if (dto.getCodpro() == null || dto.getCodpro().isBlank()) {
            return ResponseEntity.badRequest().body("Il campo codpro è obbligatorio");
        }

        if (dto.getName() == null || dto.getName().isBlank()) {
            return ResponseEntity.badRequest().body("Il campo name è obbligatorio");
        }

        if (dto.getCategoryId() == null) {
            return ResponseEntity.badRequest().body("Il campo categoryId è obbligatorio");
        }

        if (dto.getStockQty() == null) {
            return ResponseEntity.badRequest().body("Il campo stockQty è obbligatorio");
        }

        if (dto.getPrice() == null) {
            return ResponseEntity.badRequest().body("Il campo price è obbligatorio");
        }

        ProductCategory category = productService.findCategoryById(dto.getCategoryId())
                .orElse(null);

        if (category == null) {
            return ResponseEntity.badRequest().body("Categoria non trovata con id: " + dto.getCategoryId());
        }

        Product product = new Product();
        product.setCodpro(dto.getCodpro().trim());
        product.setName(dto.getName().trim());
        product.setCategory(category);
        product.setStockQty(dto.getStockQty());
        product.setPrice(dto.getPrice());

        product.setDescription(dto.getDescription());
        product.setMaterials(dto.getMaterials());
        product.setShippingInfo(dto.getShippingInfo());
        product.setImage(dto.getImage());
        product.setGroupId(dto.getGroupId());
        product.setSubgroupId(dto.getSubgroupId());
        product.setBrandId(dto.getBrandId());
        product.setFamilyId(dto.getFamilyId());
        product.setSubfamilyId(dto.getSubfamilyId());

        Product saved = productService.save(product);

        return ResponseEntity.ok(ProductMapper.toDto(saved));
    }
}