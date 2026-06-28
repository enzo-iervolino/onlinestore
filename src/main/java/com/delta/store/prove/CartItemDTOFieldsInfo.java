package com.delta.store.prove;

 
 

import java.util.List;

public final class CartItemDTOFieldsInfo {

    private CartItemDTOFieldsInfo() {
    }

    public static List<String> allFields() {
        return List.of(
                "productId",
                "variantId",
                "productName",
                "sku",
                "productCode",
                "variantCode",
                "color",
                "size",
                "quantity",
                "unitPrice"
        );
    }

    public static String asText() {
        return String.join(System.lineSeparator(), allFields());
    }
}