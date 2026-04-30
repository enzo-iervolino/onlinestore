package com.delta.store.product.repository;

import com.delta.store.product.model.SizeOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SizeOptionRepository extends JpaRepository<SizeOption, Long> {

    Optional<SizeOption> findByCodeIgnoreCase(String code);

    List<SizeOption> findAllByOrderByIdAsc();

    List<SizeOption> findByNameItContainingIgnoreCaseOrCodeContainingIgnoreCaseOrderByIdAsc(String name, String code);
}