package com.delta.store.product.service;

import com.delta.store.product.model.ColorOption;
import com.delta.store.product.repository.ColorOptionRepository;
import com.delta.store.product.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ColorOptionServiceImpl implements ColorOptionService {

    private static final String DELETE_BLOCKED_MESSAGE =
            "Impossibile eliminare il colore perché è associato a una o più varianti prodotto.";

    private final ColorOptionRepository repo;
    private final ProductVariantRepository productVariantRepository;

    public ColorOptionServiceImpl(ColorOptionRepository repo,
                                  ProductVariantRepository productVariantRepository) {
        this.repo = repo;
        this.productVariantRepository = productVariantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ColorOption> findAll() {
        return repo.findAllByOrderByNameItAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ColorOption> search(String q) {
        if (q == null || q.isBlank()) {
            return findAll();
        }
        String term = q.trim();
        return repo.findByNameItContainingIgnoreCaseOrCodeContainingIgnoreCaseOrderByNameItAsc(term, term);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ColorOption> findById(Long id) {
        return repo.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ColorOption> findByCodeIgnoreCase(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return repo.findByCodeIgnoreCase(code.trim());
    }

    @Override
    public ColorOption save(ColorOption color) {
        return repo.save(color);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUsedByAnyVariant(Long id) {
        if (id == null) {
            return false;
        }
        return productVariantRepository.existsByColor_Id(id);
    }

    @Override
    public void deleteById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID colore non valido.");
        }

        if (!repo.existsById(id)) {
            throw new IllegalArgumentException("Colore non trovato.");
        }

        if (productVariantRepository.existsByColor_Id(id)) {
            throw new IllegalStateException(DELETE_BLOCKED_MESSAGE);
        }

        repo.deleteById(id);
    }
}