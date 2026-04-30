package com.delta.store.prove;

 

import java.util.List;

public final class CartItemFieldsInfo {

    private CartItemFieldsInfo() {
    }

    public static List<String> allFields() {
        return List.of(
                "id",
                "productId",
                "variantId",
                "productCode",
                "variantCode",
                "name",
                "image",
                "color",
                "size",
                "price",
                "quantity"
        );
    }

    public static String asText() {
        return String.join(System.lineSeparator(), allFields());
    }
}