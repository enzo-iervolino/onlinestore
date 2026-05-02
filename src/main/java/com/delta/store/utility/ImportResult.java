package com.delta.store.utility;

import com.delta.store.product.dto.ProductDTO;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {

    private int totalRows;
    private int importedRows;
    private int skippedRows;

    private final List<String> errors = new ArrayList<>();
    private final List<ProductDTO> importedItems = new ArrayList<>();

    public int getTotalRows() {
        return totalRows;
    }

    public int getImportedRows() {
        return importedRows;
    }

    public int getSkippedRows() {
        return skippedRows;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<ProductDTO> getImportedItems() {
        return importedItems;
    }

    public void incrementTotalRows() {
        this.totalRows++;
    }

    public void incrementImportedRows() {
        this.importedRows++;
    }

    public void incrementSkippedRows() {
        this.skippedRows++;
    }

    public void addError(String error) {
        this.errors.add(error);
    }

    public void addImportedItem(ProductDTO item) {
        this.importedItems.add(item);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}