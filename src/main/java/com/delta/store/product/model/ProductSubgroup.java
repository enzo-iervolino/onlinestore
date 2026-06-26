package com.delta.store.product.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "product_subgroups",
        indexes = {
                @Index(name = "idx_product_subgroups_code", columnList = "code", unique = true)
        }
)
public class ProductSubgroup extends AbstractImageLookupEntity {
}
