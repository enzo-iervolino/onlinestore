package com.delta.store.user.controller;

import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.model.GiftCardUsage;
import com.delta.store.giftcard.repository.GiftCardRepository;
import com.delta.store.giftcard.repository.GiftCardUsageRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/profile")
public class ProfileGiftCardRestController {

    private final GiftCardRepository giftCardRepository;
    private final GiftCardUsageRepository giftCardUsageRepository;
    private final EntityManager entityManager;

    public ProfileGiftCardRestController(GiftCardRepository giftCardRepository,
                                         GiftCardUsageRepository giftCardUsageRepository,
                                         EntityManager entityManager) {
        this.giftCardRepository = giftCardRepository;
        this.giftCardUsageRepository = giftCardUsageRepository;
        this.entityManager = entityManager;
    }

    @GetMapping("/giftcards")
    @Transactional(readOnly = true)
    public List<UserGiftCardRowDTO> myGiftCards(@RequestParam(required = false) Long userId,
                                                Authentication authentication) {

        Long currentUserId = resolveCurrentUserId(authentication);

        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utente non autenticato");
        }

        if (userId != null && !userId.equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accesso non consentito");
        }

        return giftCardRepository.findByCustomerIdOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(UserGiftCardRowDTO::from)
                .toList();
    }

    @GetMapping("/giftcards/{giftCardId}")
    @Transactional(readOnly = true)
    public UserGiftCardDetailDTO giftCardDetail(@PathVariable Long giftCardId,
                                                Authentication authentication) {

        Long currentUserId = resolveCurrentUserId(authentication);

        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utente non autenticato");
        }

        GiftCard giftCard = giftCardRepository.findById(giftCardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gift card non trovata"));

        if (giftCard.getCustomerId() == null || !giftCard.getCustomerId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accesso non consentito");
        }

        List<GiftCardUsage> usages = giftCardUsageRepository.findByGiftCard_IdOrderByUsedAtAsc(giftCard.getId());
        return UserGiftCardDetailDTO.from(giftCard, usages);
    }

    private Long resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String usernameOrEmail = authentication.getName();
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            return null;
        }

        try {
            return entityManager.createQuery(
                            "select u.id from User u where lower(u.email) = :email",
                            Long.class)
                    .setParameter("email", usernameOrEmail.trim().toLowerCase())
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    public record UserGiftCardRowDTO(
            Long id,
            String code,
            BigDecimal initialAmount,
            BigDecimal remainingAmount,
            String recipient,
            String recipientFirstName,
            String recipientLastName,
            String recipientEmail,
            String expirationDate,
            boolean active,
            boolean used,
            String status
    ) {
        public static UserGiftCardRowDTO from(GiftCard card) {
            return new UserGiftCardRowDTO(
                    card.getId(),
                    card.getCode(),
                    card.getInitialAmount(),
                    card.getRemainingAmount(),
                    buildRecipient(card),
                    card.getRecipientFirstName(),
                    card.getRecipientLastName(),
                    card.getRecipientEmail(),
                    formatDate(card.getExpirationDate()),
                    card.isActive(),
                    card.isUsed(),
                    resolveStatus(card)
            );
        }
    }

    public record UserGiftCardDetailDTO(
            Long id,
            String code,
            BigDecimal initialAmount,
            BigDecimal remainingAmount,
            String recipient,
            String recipientFirstName,
            String recipientLastName,
            String recipientEmail,
            String personalMessage,
            String createdAt,
            String expirationDate,
            boolean active,
            boolean used,
            String status,
            List<UserGiftCardMovementDTO> movements
    ) {
        public static UserGiftCardDetailDTO from(GiftCard card, List<GiftCardUsage> usages) {
            List<UserGiftCardMovementDTO> movements = buildMovements(card, usages);

            return new UserGiftCardDetailDTO(
                    card.getId(),
                    card.getCode(),
                    card.getInitialAmount(),
                    card.getRemainingAmount(),
                    buildRecipient(card),
                    card.getRecipientFirstName(),
                    card.getRecipientLastName(),
                    card.getRecipientEmail(),
                    card.getPersonalMessage(),
                    formatDateTime(card.getCreatedAt()),
                    formatDate(card.getExpirationDate()),
                    card.isActive(),
                    card.isUsed(),
                    resolveStatus(card),
                    movements
            );
        }
    }

    public record UserGiftCardMovementDTO(
            String movementDate,
            String type,
            Long orderId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String description
    ) {
    }

    private static List<UserGiftCardMovementDTO> buildMovements(GiftCard card, List<GiftCardUsage> usages) {
        List<UserGiftCardMovementDTO> rows = new ArrayList<>();

        BigDecimal initialAmount = safeAmount(card.getInitialAmount());
        BigDecimal runningBalance = initialAmount;

        rows.add(new UserGiftCardMovementDTO(
                formatDateTime(card.getCreatedAt()),
                "EMISSIONE",
                null,
                initialAmount,
                runningBalance,
                "Emissione gift card"
        ));

        if (usages != null) {
            for (GiftCardUsage usage : usages) {
                BigDecimal amountUsed = safeAmount(usage.getAmountUsed());
                runningBalance = runningBalance.subtract(amountUsed);
                if (runningBalance.compareTo(BigDecimal.ZERO) < 0) {
                    runningBalance = BigDecimal.ZERO;
                }

                rows.add(new UserGiftCardMovementDTO(
                        formatDateTime(usage.getUsedAt()),
                        "UTILIZZO",
                        usage.getOrderId(),
                        amountUsed.negate(),
                        runningBalance,
                        usage.getOrderId() != null
                                ? "Utilizzo su ordine #" + usage.getOrderId()
                                : "Utilizzo gift card"
                ));
            }
        }

        return rows;
    }

    private static BigDecimal safeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String buildRecipient(GiftCard card) {
        StringBuilder sb = new StringBuilder();

        append(sb, card.getRecipientFirstName());
        append(sb, card.getRecipientLastName());

        String email = clean(card.getRecipientEmail());
        if (email != null) {
            if (!sb.isEmpty()) {
                sb.append(" - ");
            }
            sb.append(email);
        }

        return sb.isEmpty() ? "—" : sb.toString();
    }

    private static void append(StringBuilder sb, String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(cleaned);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String resolveStatus(GiftCard card) {
        if (card == null) {
            return "—";
        }
        if (!card.isActive()) {
            return "DISATTIVATA";
        }
        if (card.getExpirationDate() != null && card.getExpirationDate().isBefore(LocalDate.now())) {
            return "SCADUTA";
        }
        if (card.isUsed() || safeAmount(card.getRemainingAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            return "ESAURITA";
        }
        return "ATTIVA";
    }

    private static String formatDate(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
