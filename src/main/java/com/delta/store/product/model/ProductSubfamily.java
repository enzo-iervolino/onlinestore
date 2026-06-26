package com.delta.store.product.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "product_subfamilies",
        indexes = {
                @Index(name = "idx_product_subfamilies_code", columnList = "code", unique = true)
        }
)
public class ProductSubfamily extends AbstractImageLookupEntity {
}
