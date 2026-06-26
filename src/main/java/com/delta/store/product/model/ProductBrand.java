package com.delta.store.product.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "product_brands",
        indexes = {
                @Index(name = "idx_product_brands_code", columnList = "code", unique = true)
        }
)
public class ProductBrand extends AbstractImageLookupEntity {
}
