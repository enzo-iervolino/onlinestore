package com.delta.store.shipping.service;

import com.delta.store.shipping.model.ShippingCost;
import com.delta.store.shipping.repository.ShippingCostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ShippingServiceImpl implements ShippingService {

    private final ShippingCostRepository shippingCostRepository;

    public ShippingServiceImpl(ShippingCostRepository shippingCostRepository) {
        this.shippingCostRepository = shippingCostRepository;
    }

    @Override
    public BigDecimal calculateShipping(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return shippingCostRepository
                .findBestMatch(subtotal)
                .map(ShippingCost::getCost)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public List<ShippingCost> findAll() {
        return shippingCostRepository.findAllByOrderByMinTotalAscMaxTotalAscIdAsc();
    }

    @Override
    public Optional<ShippingCost> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return shippingCostRepository.findById(id);
    }

    @Override
    @Transactional
    public ShippingCost save(ShippingCost shippingCost) {
        return shippingCostRepository.save(shippingCost);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        shippingCostRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return id != null && shippingCostRepository.existsById(id);
    }

    @Override
    public boolean hasActiveOverlap(Long excludedId, BigDecimal minTotal, BigDecimal maxTotal) {
        if (minTotal == null) {
            return false;
        }

        return findAll().stream()
                .filter(ShippingCost::isActive)
                .filter(item -> excludedId == null || !excludedId.equals(item.getId()))
                .anyMatch(item -> rangesOverlap(
                        minTotal,
                        maxTotal,
                        item.getMinTotal(),
                        item.getMaxTotal()
                ));
    }

    private boolean rangesOverlap(BigDecimal firstMin,
                                  BigDecimal firstMax,
                                  BigDecimal secondMin,
                                  BigDecimal secondMax) {
        boolean firstStartsBeforeSecondEnds = secondMax == null
                || firstMin.compareTo(secondMax) <= 0;

        boolean secondStartsBeforeFirstEnds = firstMax == null
                || secondMin.compareTo(firstMax) <= 0;

        return firstStartsBeforeSecondEnds && secondStartsBeforeFirstEnds;
    }
}
