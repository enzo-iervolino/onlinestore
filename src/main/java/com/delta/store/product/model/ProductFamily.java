package com.delta.store.product.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "product_families",
        indexes = {
                @Index(name = "idx_product_families_code", columnList = "code", unique = true)
        }
)
public class ProductFamily extends AbstractImageLookupEntity {
}
