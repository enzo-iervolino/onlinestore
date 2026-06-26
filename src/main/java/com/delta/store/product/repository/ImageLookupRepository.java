package com.delta.store.product.repository;

import com.delta.store.product.model.ImageLookupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface ImageLookupRepository<T extends ImageLookupEntity> extends JpaRepository<T, Long> {

    Optional<T> findByCode(String code);

    List<T> findAllByOrderByCodeAsc();
}
