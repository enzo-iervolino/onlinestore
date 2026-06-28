package com.delta.store.giftcard.service;

import com.delta.store.giftcard.model.GiftCardUsage;
import com.delta.store.giftcard.repository.GiftCardUsageRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GiftCardAdminService {

    private final GiftCardUsageRepository repository;

    public GiftCardAdminService(GiftCardUsageRepository repository) {
        this.repository = repository;
    }

    public List<GiftCardUsage> searchMovements(String code,
                                               LocalDate from,
                                               LocalDate to) {

        boolean hasCode = code != null && !code.isBlank();
        boolean hasDate = from != null && to != null;

        // Nessun filtro
        if (!hasCode && !hasDate) {
            return repository.findAllByOrderByUsedAtDesc();
        }

        // Solo codice
        if (hasCode && !hasDate) {
            return repository
                    .findByGiftCard_CodeContainingIgnoreCaseOrderByUsedAtDesc(code);
        }

        // Solo date
        if (!hasCode) {
            return repository
                    .findByUsedAtBetweenOrderByUsedAtDesc(
                            from.atStartOfDay(),
                            to.atTime(23, 59, 59)
                    );
        }

        // Codice + date
        LocalDateTime fromDate = from.atStartOfDay();
        LocalDateTime toDate = to.atTime(23, 59, 59);

        return repository
                .findByGiftCard_CodeContainingIgnoreCaseOrderByUsedAtDesc(code)
                .stream()
                .filter(u -> !u.getUsedAt().isBefore(fromDate)
                        && !u.getUsedAt().isAfter(toDate))
                .collect(Collectors.toList());
    }
}
