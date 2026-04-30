package com.delta.store.product.repository;

import com.delta.store.product.model.ColorOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ColorOptionRepository extends JpaRepository<ColorOption, Long> {

    Optional<ColorOption> findByCodeIgnoreCase(String code);

    List<ColorOption> findAllByOrderByNameItAsc();

    List<ColorOption> findByNameItContainingIgnoreCaseOrCodeContainingIgnoreCaseOrderByNameItAsc(String name, String code);
}