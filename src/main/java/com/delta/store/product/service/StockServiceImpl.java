package com.delta.store.product.service;

import com.delta.store.order.model.Order;
import com.delta.store.order.model.OrderItem;
import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductVariant;
import com.delta.store.product.repository.ProductRepository;
import com.delta.store.product.repository.ProductVariantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StockServiceImpl implements StockService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    public StockServiceImpl(ProductRepository productRepository,
                            ProductVariantRepository productVariantRepository) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @Override
    public void decrementStockForPaidOrder(Order order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }

        for (OrderItem item : order.getItems()) {
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            if (qty <= 0) {
                continue;
            }

            if (item.getVariantId() != null) {
                decrementVariantStock(item.getVariantId(), qty, item.getVariantCode());
            } else if (item.getProductId() != null) {
                decrementProductStock(item.getProductId(), qty, item.getProductCode());
            } else {
                throw new IllegalStateException("OrderItem senza productId e senza variantId");
            }
        }
    }

    private void decrementVariantStock(Long variantId, int qty, String variantCode) {
        ProductVariant variant = productVariantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new EntityNotFoundException("Variante non trovata: " + variantId));

        Integer currentStock = variant.getStockQty() != null ? variant.getStockQty() : 0;

        if (currentStock < qty) {
            throw new IllegalStateException(
                    "Stock insufficiente per la variante "
                            + (variantCode != null ? variantCode : variantId)
                            + ". Disponibile=" + currentStock + ", richiesto=" + qty
            );
        }

        variant.setStockQty(currentStock - qty);
        productVariantRepository.save(variant);
    }

    private void decrementProductStock(Long productId, int qty, String productCode) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new EntityNotFoundException("Prodotto non trovato: " + productId));

        Integer currentStock = product.getStockQty() != null ? product.getStockQty() : 0;

        if (currentStock < qty) {
            throw new IllegalStateException(
                    "Stock insufficiente per il prodotto "
                            + (productCode != null ? productCode : productId)
                            + ". Disponibile=" + currentStock + ", richiesto=" + qty
            );
        }

        product.setStockQty(currentStock - qty);
        productRepository.save(product);
    }
}