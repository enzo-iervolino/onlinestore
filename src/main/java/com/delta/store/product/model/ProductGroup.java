package com.delta.store.product.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "product_groups",
        indexes = {
                @Index(name = "idx_product_groups_code", columnList = "code", unique = true)
        }
)
public class ProductGroup extends AbstractImageLookupEntity {
}
