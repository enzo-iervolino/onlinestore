package com.delta.store.product.service;

import com.delta.store.product.model.ColorOption;

import java.util.List;
import java.util.Optional;

public interface ColorOptionService {

    List<ColorOption> findAll();

    List<ColorOption> search(String q);

    Optional<ColorOption> findById(Long id);

    Optional<ColorOption> findByCodeIgnoreCase(String code);

    ColorOption save(ColorOption color);

    boolean isUsedByAnyVariant(Long id);

    void deleteById(Long id);
}