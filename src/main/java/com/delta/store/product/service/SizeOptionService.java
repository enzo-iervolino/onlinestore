package com.delta.store.product.service;

import com.delta.store.product.model.SizeOption;

import java.util.List;
import java.util.Optional;

public interface SizeOptionService {

    List<SizeOption> findAll();

    List<SizeOption> search(String q);

    Optional<SizeOption> findById(Long id);

    Optional<SizeOption> findByCodeIgnoreCase(String code);

    SizeOption save(SizeOption size);

    void deleteById(Long id);
}
