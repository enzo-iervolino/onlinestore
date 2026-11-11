package com.delta.store.product.model;

public enum ArticleCodeType {

    INTERNAL("Codice interno"),
    EAN("EAN"),
    EAN13("EAN-13"),
    EAN8("EAN-8"),
    UPC("UPC"),
    BARCODE("Barcode"),
    SUPPLIER("Codice fornitore"),
    OTHER("Altro");

    private final String label;

    ArticleCodeType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
