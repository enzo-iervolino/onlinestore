package com.delta.store.product.repository;

import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByCategoryOrderByIdDesc(ProductCategory category);

    @Query("""
           select p
           from Product p
           where (:category is null or p.category = :category)
             and lower(p.name) like lower(concat('%', :text, '%'))
           order by p.id desc
           """)
    List<Product> search(@Param("category") ProductCategory category,
                         @Param("text") String text);

    @Query("""
           select p
           from Product p
           order by p.id desc
           """)
    List<Product> findLatest();

    @EntityGraph(attributePaths = {"category"})
    List<Product> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = {
            "category",
            "images",
            "variants",
            "variants.color",
            "variants.size",
            "variants.images"
    })
    Optional<Product> findWithImagesById(Long id);

    Optional<Product> findByPublicId(UUID publicId);

    Optional<Product> findByCodpro(String codpro);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}