package com.delta.store.discount.service;

import com.delta.store.discount.model.DiscountCode;
import com.delta.store.discount.repository.DiscountCodeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DiscountService {

    private final DiscountCodeRepository repository;

    public DiscountService(DiscountCodeRepository repository) {
        this.repository = repository;
    }

    public Optional<DiscountCode> validateCode(String code) {

        Optional<DiscountCode> optional =
                repository.findByCodeIgnoreCaseAndStatus(
                        code,
                        DiscountCode.Status.ATTIVO
                );

        if (optional.isEmpty()) {
            return Optional.empty();
        }

        DiscountCode dc = optional.get();

        if (dc.getExpiresAt() != null &&
                dc.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }

        return Optional.of(dc);
    }

    public BigDecimal calculateDiscount(BigDecimal subtotal, Integer percentage) {

        if (subtotal == null || percentage == null) {
            return BigDecimal.ZERO;
        }

        return subtotal
                .multiply(BigDecimal.valueOf(percentage))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    // =========================
    // CRUD ADMIN
    // =========================

    public List<DiscountCode> findAll() {
        List<DiscountCode> list = repository.findAll();

        for (DiscountCode dc : list) {
            refreshStatusIfExpired(dc);
        }

        return repository.findAll();
    }

    public Optional<DiscountCode> findById(Long id) {
        Optional<DiscountCode> optional = repository.findById(id);
        optional.ifPresent(this::refreshStatusIfExpired);
        return repository.findById(id);
    }

    public DiscountCode create(DiscountCode discountCode) {
        normalize(discountCode);
        applyExpiredStatus(discountCode);
        return repository.save(discountCode);
    }

    public DiscountCode update(Long id, DiscountCode formData) {
        DiscountCode existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Buono sconto non trovato con id: " + id));

        existing.setCode(formData.getCode());
        existing.setPercentage(formData.getPercentage());
        existing.setExpiresAt(formData.getExpiresAt());
        existing.setStatus(formData.getStatus());

        normalize(existing);
        applyExpiredStatus(existing);

        return repository.save(existing);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    private void normalize(DiscountCode discountCode) {
        if (discountCode.getCode() != null) {
            discountCode.setCode(discountCode.getCode().trim().toUpperCase());
        }

        if (discountCode.getStatus() == null) {
            discountCode.setStatus(DiscountCode.Status.ATTIVO);
        }
    }

    private void applyExpiredStatus(DiscountCode discountCode) {
        if (discountCode.getExpiresAt() != null &&
                discountCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            discountCode.setStatus(DiscountCode.Status.SCADUTO);
        }
    }

    private void refreshStatusIfExpired(DiscountCode discountCode) {
        if (discountCode.getExpiresAt() != null
                && discountCode.getExpiresAt().isBefore(LocalDateTime.now())
                && discountCode.getStatus() != DiscountCode.Status.SCADUTO) {

            discountCode.setStatus(DiscountCode.Status.SCADUTO);
            repository.save(discountCode);
        }
    }
}