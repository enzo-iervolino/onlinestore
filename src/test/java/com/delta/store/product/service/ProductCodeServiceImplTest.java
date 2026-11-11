package com.delta.store.product.service;

import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductCode;
import com.delta.store.product.repository.ProductCodeRepository;
import com.delta.store.product.repository.ProductRepository;
import com.delta.store.product.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCodeServiceImplTest {

    @Mock
    private ProductCodeRepository productCodeRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private ProductCodeServiceImpl productCodeService;

    private final List<ProductCode> storedCodes = new ArrayList<>();

    @BeforeEach
    void setUpRepositories() {
        storedCodes.clear();

        when(productCodeRepository.findByNormalizedCode(anyString()))
                .thenAnswer(invocation -> storedCodes.stream()
                        .filter(code -> invocation.getArgument(0).equals(code.getNormalizedCode()))
                        .findFirst());
        when(productRepository.findByCodproIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(productVariantRepository.findByVariantCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(productCodeRepository.save(any(ProductCode.class))).thenAnswer(invocation -> {
            ProductCode code = invocation.getArgument(0);
            storedCodes.add(code);
            return code;
        });
        when(productCodeRepository.findByProduct_IdOrderByCodeTypeAscCodeValueAsc(any()))
                .thenAnswer(invocation -> storedCodes.stream()
                        .filter(code -> code.getProduct() != null
                                && invocation.getArgument(0).equals(code.getProduct().getId()))
                        .toList());
    }

    @Test
    void replaceProductCodesPersistsEverySubmittedCode() {
        Product product = product(15L, "ART-001");
        when(productRepository.findById(15L)).thenReturn(Optional.of(product));

        int saved = productCodeService.replaceProductCodes(
                product,
                "EAN13:8001234567890\nBARCODE:123456789\nFORNITORE:SUP-001"
        );

        assertThat(saved).isEqualTo(3);
        assertThat(storedCodes)
                .hasSize(3)
                .extracting(ProductCode::getCodeValue)
                .containsExactly("8001234567890", "123456789", "SUP-001");
    }

    @Test
    void replaceProductCodesAcceptsCommonMulticodeSeparators() {
        Product product = product(16L, "ART-002");
        when(productRepository.findById(16L)).thenReturn(Optional.of(product));

        int saved = productCodeService.replaceProductCodes(
                product,
                "EAN13:8012345678907; BARCODE:BC-002 | FORNITORE:SUP-002\tALTRO:TEST-002"
        );

        assertThat(saved).isEqualTo(4);
        assertThat(storedCodes).hasSize(4);
    }

    private Product product(Long id, String codpro) {
        Product product = new Product();
        product.setId(id);
        product.setCodpro(codpro);
        product.setName("Prodotto test");
        return product;
    }
}
