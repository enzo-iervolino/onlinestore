package com.delta.store.giftcard.service;

import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.model.GiftCardPaymentMethod;
import com.delta.store.giftcard.model.GiftCardPurchase;
import com.delta.store.giftcard.model.GiftCardPurchaseStatus;
import com.delta.store.giftcard.model.GiftCardUsage;
import com.delta.store.giftcard.repository.GiftCardPurchaseRepository;
import com.delta.store.giftcard.repository.GiftCardRepository;
import com.delta.store.giftcard.repository.GiftCardUsageRepository;
import com.delta.store.user.model.User;
import com.delta.store.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GiftCardAdminService {

    private final GiftCardUsageRepository usageRepository;
    private final GiftCardRepository giftCardRepository;
    private final GiftCardPurchaseRepository purchaseRepository;
    private final UserRepository userRepository;

    public GiftCardAdminService(GiftCardUsageRepository usageRepository,
                                GiftCardRepository giftCardRepository,
                                GiftCardPurchaseRepository purchaseRepository,
                                UserRepository userRepository) {
        this.usageRepository = usageRepository;
        this.giftCardRepository = giftCardRepository;
        this.purchaseRepository = purchaseRepository;
        this.userRepository = userRepository;
    }

    public List<GiftCardUsage> searchMovements(String code,
                                               LocalDate from,
                                               LocalDate to) {

        boolean hasCode = code != null && !code.isBlank();
        boolean hasDate = from != null && to != null;

        if (!hasCode && !hasDate) {
            return usageRepository.findAllByOrderByUsedAtDesc();
        }

        if (hasCode && !hasDate) {
            return usageRepository
                    .findByGiftCard_CodeContainingIgnoreCaseOrderByUsedAtDesc(code);
        }

        if (!hasCode) {
            return usageRepository
                    .findByUsedAtBetweenOrderByUsedAtDesc(
                            from.atStartOfDay(),
                            to.atTime(23, 59, 59)
                    );
        }

        LocalDateTime fromDate = from.atStartOfDay();
        LocalDateTime toDate = to.atTime(23, 59, 59);

        return usageRepository
                .findByGiftCard_CodeContainingIgnoreCaseOrderByUsedAtDesc(code)
                .stream()
                .filter(u -> u.getUsedAt() != null
                        && !u.getUsedAt().isBefore(fromDate)
                        && !u.getUsedAt().isAfter(toDate))
                .collect(Collectors.toList());
    }

    public List<AdminGiftCardRow> searchCards(String query, String status) {
        return searchCards(query, status, null, null, null, null, null, null);
    }

    public List<AdminGiftCardRow> searchCards(String query,
                                              String status,
                                              String paymentMethod,
                                              String purchaseStatus,
                                              LocalDate from,
                                              LocalDate to,
                                              BigDecimal minAmount,
                                              BigDecimal maxAmount) {
        String normalizedQuery = clean(query);
        String normalizedStatus = normalizeFilter(status);
        String normalizedPaymentMethod = normalizeFilter(paymentMethod);
        String normalizedPurchaseStatus = normalizeFilter(purchaseStatus);

        LocalDate normalizedFrom = from;
        LocalDate normalizedTo = to;
        if (normalizedFrom != null && normalizedTo != null && normalizedFrom.isAfter(normalizedTo)) {
            LocalDate tmp = normalizedFrom;
            normalizedFrom = normalizedTo;
            normalizedTo = tmp;
        }

        BigDecimal normalizedMinAmount = minAmount;
        BigDecimal normalizedMaxAmount = maxAmount;
        if (normalizedMinAmount != null
                && normalizedMaxAmount != null
                && normalizedMinAmount.compareTo(normalizedMaxAmount) > 0) {
            BigDecimal tmp = normalizedMinAmount;
            normalizedMinAmount = normalizedMaxAmount;
            normalizedMaxAmount = tmp;
        }

        LocalDate finalFrom = normalizedFrom;
        LocalDate finalTo = normalizedTo;
        BigDecimal finalMinAmount = normalizedMinAmount;
        BigDecimal finalMaxAmount = normalizedMaxAmount;

        return giftCardRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(card -> matchesStatus(card, normalizedStatus))
                .filter(card -> matchesDate(card, finalFrom, finalTo))
                .filter(card -> matchesAmount(card, finalMinAmount, finalMaxAmount))
                .map(this::toAdminRow)
                .filter(row -> matchesQuery(row, normalizedQuery))
                .filter(row -> matchesExactFilter(row.getPaymentMethod(), normalizedPaymentMethod))
                .filter(row -> matchesExactFilter(row.getPurchaseStatus(), normalizedPurchaseStatus))
                .collect(Collectors.toList());
    }

    public AdminGiftCardSummary buildSummary() {
        List<AdminGiftCardRow> cards = giftCardRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toAdminRow)
                .collect(Collectors.toList());

        return buildSummary(cards);
    }

    public AdminGiftCardSummary buildSummary(List<AdminGiftCardRow> cards) {
        List<AdminGiftCardRow> safeCards = cards == null ? List.of() : cards;

        long total = safeCards.size();
        long active = safeCards.stream().filter(AdminGiftCardRow::isActive).count();
        long inactive = safeCards.stream().filter(card -> !card.isActive()).count();
        long valid = safeCards.stream().filter(card -> "VALIDA".equals(card.getStatus())).count();
        long expired = safeCards.stream().filter(GiftCardAdminService::rowIsExpired).count();
        long used = safeCards.stream().filter(GiftCardAdminService::rowIsUsedOrEmpty).count();
        long withBalance = safeCards.stream()
                .filter(card -> safeAmount(card.getRemainingAmount()).compareTo(BigDecimal.ZERO) > 0)
                .count();

        BigDecimal totalAmount = sumInitialAmount(safeCards, card -> true);
        BigDecimal activeAmount = sumRemainingAmount(safeCards, AdminGiftCardRow::isActive);
        BigDecimal inactiveAmount = sumRemainingAmount(safeCards, card -> !card.isActive());
        BigDecimal validAmount = sumRemainingAmount(safeCards, card -> "VALIDA".equals(card.getStatus()));
        BigDecimal expiredAmount = sumRemainingAmount(safeCards, GiftCardAdminService::rowIsExpired);
        BigDecimal usedAmount = sumInitialAmount(safeCards, GiftCardAdminService::rowIsUsedOrEmpty);
        BigDecimal withBalanceAmount = sumRemainingAmount(safeCards,
                card -> safeAmount(card.getRemainingAmount()).compareTo(BigDecimal.ZERO) > 0);

        return new AdminGiftCardSummary(
                total,
                active,
                inactive,
                valid,
                expired,
                used,
                withBalance,
                totalAmount,
                activeAmount,
                inactiveAmount,
                validAmount,
                expiredAmount,
                usedAmount,
                withBalanceAmount
        );
    }

    private AdminGiftCardRow toAdminRow(GiftCard card) {
        Optional<User> requester = Optional.empty();
        if (card.getCustomerId() != null) {
            requester = userRepository.findById(card.getCustomerId());
        }

        Optional<GiftCardPurchase> purchase = purchaseRepository.findByGiftCard_Id(card.getId());
        List<GiftCardUsage> usages = usageRepository.findByGiftCard_IdOrderByUsedAtAsc(card.getId());
        List<AdminGiftCardMovementRow> movements = buildMovements(card, usages);

        String status = resolveStatus(card);

        return new AdminGiftCardRow(
                card.getId(),
                card.getCode(),
                card.getCustomerId(),
                buildRequesterLabel(card.getCustomerId(), requester.orElse(null)),
                requester.map(User::getEmail).orElse(null),
                safeAmount(card.getInitialAmount()),
                safeAmount(card.getRemainingAmount()),
                card.getRecipientFirstName(),
                card.getRecipientLastName(),
                card.getRecipientEmail(),
                buildRecipient(card),
                card.getPersonalMessage(),
                card.getCreatedAt(),
                card.getExpirationDate(),
                card.getValidityMonths(),
                card.isActive(),
                card.isUsed(),
                status,
                statusCss(status),
                purchase.map(GiftCardPurchase::getPaymentMethod).map(GiftCardPaymentMethod::name).orElse("—"),
                purchase.map(GiftCardPurchase::getStatus).map(GiftCardPurchaseStatus::name).orElse("—"),
                purchase.map(GiftCardPurchase::getProviderReference).orElse(null),
                purchase.map(GiftCardPurchase::getStripeSessionId).orElse(null),
                purchase.map(GiftCardPurchase::getPaypalOrderId).orElse(null),
                purchase.map(GiftCardPurchase::getPaidAt).orElse(null),
                movements
        );
    }

    private List<AdminGiftCardMovementRow> buildMovements(GiftCard card, List<GiftCardUsage> usages) {
        List<AdminGiftCardMovementRow> rows = new ArrayList<>();

        BigDecimal initialAmount = safeAmount(card.getInitialAmount());
        BigDecimal runningBalance = initialAmount;

        rows.add(new AdminGiftCardMovementRow(
                card.getCreatedAt(),
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

                rows.add(new AdminGiftCardMovementRow(
                        usage.getUsedAt(),
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

    private boolean matchesQuery(AdminGiftCardRow row, String query) {
        if (query == null) {
            return true;
        }

        return contains(row.getCode(), query)
                || contains(row.getRecipient(), query)
                || contains(row.getRecipientEmail(), query)
                || contains(row.getRequesterLabel(), query)
                || contains(row.getRequesterEmail(), query)
                || contains(row.getPaymentMethod(), query)
                || contains(row.getPurchaseStatus(), query)
                || contains(row.getStripeSessionId(), query)
                || contains(row.getPaypalOrderId(), query)
                || contains(row.getCustomerId() != null ? String.valueOf(row.getCustomerId()) : null, query);
    }

    private boolean matchesStatus(GiftCard card, String status) {
        return switch (status) {
            case "ACTIVE" -> card.isActive();
            case "INACTIVE" -> !card.isActive();
            case "VALID" -> isValid(card);
            case "EXPIRED" -> isExpired(card);
            case "USED" -> isUsedOrEmpty(card);
            case "WITH_BALANCE" -> safeAmount(card.getRemainingAmount()).compareTo(BigDecimal.ZERO) > 0;
            default -> true;
        };
    }

    private boolean matchesDate(GiftCard card, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }

        if (card.getCreatedAt() == null) {
            return false;
        }

        LocalDate createdDate = card.getCreatedAt().toLocalDate();
        if (from != null && createdDate.isBefore(from)) {
            return false;
        }
        return to == null || !createdDate.isAfter(to);
    }

    private boolean matchesAmount(GiftCard card, BigDecimal minAmount, BigDecimal maxAmount) {
        BigDecimal amount = safeAmount(card.getInitialAmount());
        if (minAmount != null && amount.compareTo(minAmount) < 0) {
            return false;
        }
        return maxAmount == null || amount.compareTo(maxAmount) <= 0;
    }

    private boolean matchesExactFilter(String value, String filter) {
        if (filter == null || "ALL".equals(filter)) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return filter.equals(value.trim().toUpperCase(Locale.ROOT));
    }

    private boolean isValid(GiftCard card) {
        return card != null
                && card.isActive()
                && !isExpired(card)
                && safeAmount(card.getRemainingAmount()).compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isExpired(GiftCard card) {
        return card != null
                && card.getExpirationDate() != null
                && card.getExpirationDate().isBefore(LocalDate.now());
    }

    private boolean isUsedOrEmpty(GiftCard card) {
        return card != null
                && (card.isUsed() || safeAmount(card.getRemainingAmount()).compareTo(BigDecimal.ZERO) <= 0);
    }

    private String resolveStatus(GiftCard card) {
        if (card == null) {
            return "—";
        }
        if (!card.isActive()) {
            return "DISATTIVATA";
        }
        if (isExpired(card)) {
            return "SCADUTA";
        }
        if (isUsedOrEmpty(card)) {
            return "ESAURITA";
        }
        return "VALIDA";
    }

    private String statusCss(String status) {
        if ("VALIDA".equals(status)) {
            return "success";
        }
        if ("SCADUTA".equals(status)) {
            return "warning";
        }
        if ("ESAURITA".equals(status)) {
            return "secondary";
        }
        if ("DISATTIVATA".equals(status)) {
            return "danger";
        }
        return "light";
    }

    private String buildRequesterLabel(Long customerId, User requester) {
        if (requester == null) {
            return customerId == null ? "—" : "Cliente ID " + customerId;
        }

        StringBuilder sb = new StringBuilder();
        append(sb, requester.getFirstname());
        append(sb, requester.getLastname());

        String fullname = clean(requester.getFullname());
        if (sb.isEmpty() && fullname != null) {
            sb.append(fullname);
        }

        if (sb.isEmpty() && requester.getEmail() != null) {
            sb.append(requester.getEmail());
        }

        if (customerId != null) {
            if (!sb.isEmpty()) {
                sb.append(" · ");
            }
            sb.append("ID ").append(customerId);
        }

        return sb.isEmpty() ? "—" : sb.toString();
    }

    private String buildRecipient(GiftCard card) {
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

    private static BigDecimal sumInitialAmount(List<AdminGiftCardRow> cards,
                                               Predicate<AdminGiftCardRow> predicate) {
        return cards.stream()
                .filter(predicate)
                .map(AdminGiftCardRow::getInitialAmount)
                .map(GiftCardAdminService::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumRemainingAmount(List<AdminGiftCardRow> cards,
                                                 Predicate<AdminGiftCardRow> predicate) {
        return cards.stream()
                .filter(predicate)
                .map(AdminGiftCardRow::getRemainingAmount)
                .map(GiftCardAdminService::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    private static boolean rowIsExpired(AdminGiftCardRow card) {
        return card != null
                && card.getExpirationDate() != null
                && card.getExpirationDate().isBefore(LocalDate.now());
    }

    private static boolean rowIsUsedOrEmpty(AdminGiftCardRow card) {
        return card != null
                && (card.isUsed() || safeAmount(card.getRemainingAmount()).compareTo(BigDecimal.ZERO) <= 0);
    }

    private static BigDecimal safeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String normalizeFilter(String value) {
        String cleaned = clean(value);
        if (cleaned == null || "ALL".equalsIgnoreCase(cleaned)) {
            return "ALL";
        }
        return cleaned.toUpperCase(Locale.ROOT);
    }

    private static boolean contains(String value, String query) {
        if (value == null || query == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
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

    public static class AdminGiftCardSummary {
        private final long total;
        private final long active;
        private final long inactive;
        private final long valid;
        private final long expired;
        private final long used;
        private final long withBalance;
        private final BigDecimal totalAmount;
        private final BigDecimal activeAmount;
        private final BigDecimal inactiveAmount;
        private final BigDecimal validAmount;
        private final BigDecimal expiredAmount;
        private final BigDecimal usedAmount;
        private final BigDecimal withBalanceAmount;

        public AdminGiftCardSummary(long total,
                                    long active,
                                    long inactive,
                                    long valid,
                                    long expired,
                                    long used,
                                    long withBalance,
                                    BigDecimal totalAmount,
                                    BigDecimal activeAmount,
                                    BigDecimal inactiveAmount,
                                    BigDecimal validAmount,
                                    BigDecimal expiredAmount,
                                    BigDecimal usedAmount,
                                    BigDecimal withBalanceAmount) {
            this.total = total;
            this.active = active;
            this.inactive = inactive;
            this.valid = valid;
            this.expired = expired;
            this.used = used;
            this.withBalance = withBalance;
            this.totalAmount = safeAmount(totalAmount);
            this.activeAmount = safeAmount(activeAmount);
            this.inactiveAmount = safeAmount(inactiveAmount);
            this.validAmount = safeAmount(validAmount);
            this.expiredAmount = safeAmount(expiredAmount);
            this.usedAmount = safeAmount(usedAmount);
            this.withBalanceAmount = safeAmount(withBalanceAmount);
        }

        public long getTotal() {
            return total;
        }

        public long getActive() {
            return active;
        }

        public long getInactive() {
            return inactive;
        }

        public long getValid() {
            return valid;
        }

        public long getExpired() {
            return expired;
        }

        public long getUsed() {
            return used;
        }

        public long getWithBalance() {
            return withBalance;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public BigDecimal getActiveAmount() {
            return activeAmount;
        }

        public BigDecimal getInactiveAmount() {
            return inactiveAmount;
        }

        public BigDecimal getValidAmount() {
            return validAmount;
        }

        public BigDecimal getExpiredAmount() {
            return expiredAmount;
        }

        public BigDecimal getUsedAmount() {
            return usedAmount;
        }

        public BigDecimal getWithBalanceAmount() {
            return withBalanceAmount;
        }
    }

    public static class AdminGiftCardRow {
        private final Long id;
        private final String code;
        private final Long customerId;
        private final String requesterLabel;
        private final String requesterEmail;
        private final BigDecimal initialAmount;
        private final BigDecimal remainingAmount;
        private final String recipientFirstName;
        private final String recipientLastName;
        private final String recipientEmail;
        private final String recipient;
        private final String personalMessage;
        private final LocalDateTime createdAt;
        private final LocalDate expirationDate;
        private final Integer validityMonths;
        private final boolean active;
        private final boolean used;
        private final String status;
        private final String statusCss;
        private final String paymentMethod;
        private final String purchaseStatus;
        private final String providerReference;
        private final String stripeSessionId;
        private final String paypalOrderId;
        private final LocalDateTime paidAt;
        private final List<AdminGiftCardMovementRow> movements;

        public AdminGiftCardRow(Long id,
                                String code,
                                Long customerId,
                                String requesterLabel,
                                String requesterEmail,
                                BigDecimal initialAmount,
                                BigDecimal remainingAmount,
                                String recipientFirstName,
                                String recipientLastName,
                                String recipientEmail,
                                String recipient,
                                String personalMessage,
                                LocalDateTime createdAt,
                                LocalDate expirationDate,
                                Integer validityMonths,
                                boolean active,
                                boolean used,
                                String status,
                                String statusCss,
                                String paymentMethod,
                                String purchaseStatus,
                                String providerReference,
                                String stripeSessionId,
                                String paypalOrderId,
                                LocalDateTime paidAt,
                                List<AdminGiftCardMovementRow> movements) {
            this.id = id;
            this.code = code;
            this.customerId = customerId;
            this.requesterLabel = requesterLabel;
            this.requesterEmail = requesterEmail;
            this.initialAmount = initialAmount;
            this.remainingAmount = remainingAmount;
            this.recipientFirstName = recipientFirstName;
            this.recipientLastName = recipientLastName;
            this.recipientEmail = recipientEmail;
            this.recipient = recipient;
            this.personalMessage = personalMessage;
            this.createdAt = createdAt;
            this.expirationDate = expirationDate;
            this.validityMonths = validityMonths;
            this.active = active;
            this.used = used;
            this.status = status;
            this.statusCss = statusCss;
            this.paymentMethod = paymentMethod;
            this.purchaseStatus = purchaseStatus;
            this.providerReference = providerReference;
            this.stripeSessionId = stripeSessionId;
            this.paypalOrderId = paypalOrderId;
            this.paidAt = paidAt;
            this.movements = movements == null ? List.of() : movements;
        }

        public Long getId() {
            return id;
        }

        public String getCode() {
            return code;
        }

        public Long getCustomerId() {
            return customerId;
        }

        public String getRequesterLabel() {
            return requesterLabel;
        }

        public String getRequesterEmail() {
            return requesterEmail;
        }

        public BigDecimal getInitialAmount() {
            return initialAmount;
        }

        public BigDecimal getRemainingAmount() {
            return remainingAmount;
        }

        public String getRecipientFirstName() {
            return recipientFirstName;
        }

        public String getRecipientLastName() {
            return recipientLastName;
        }

        public String getRecipientEmail() {
            return recipientEmail;
        }

        public String getRecipient() {
            return recipient;
        }

        public String getPersonalMessage() {
            return personalMessage;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public LocalDate getExpirationDate() {
            return expirationDate;
        }

        public Integer getValidityMonths() {
            return validityMonths;
        }

        public boolean isActive() {
            return active;
        }

        public boolean isUsed() {
            return used;
        }

        public String getStatus() {
            return status;
        }

        public String getStatusCss() {
            return statusCss;
        }

        public String getPaymentMethod() {
            return paymentMethod;
        }

        public String getPurchaseStatus() {
            return purchaseStatus;
        }

        public String getProviderReference() {
            return providerReference;
        }

        public String getStripeSessionId() {
            return stripeSessionId;
        }

        public String getPaypalOrderId() {
            return paypalOrderId;
        }

        public LocalDateTime getPaidAt() {
            return paidAt;
        }

        public List<AdminGiftCardMovementRow> getMovements() {
            return movements;
        }
    }

    public static class AdminGiftCardMovementRow {
        private final LocalDateTime movementDate;
        private final String type;
        private final Long orderId;
        private final BigDecimal amount;
        private final BigDecimal balanceAfter;
        private final String description;

        public AdminGiftCardMovementRow(LocalDateTime movementDate,
                                        String type,
                                        Long orderId,
                                        BigDecimal amount,
                                        BigDecimal balanceAfter,
                                        String description) {
            this.movementDate = movementDate;
            this.type = type;
            this.orderId = orderId;
            this.amount = amount;
            this.balanceAfter = balanceAfter;
            this.description = description;
        }

        public LocalDateTime getMovementDate() {
            return movementDate;
        }

        public String getType() {
            return type;
        }

        public Long getOrderId() {
            return orderId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public BigDecimal getBalanceAfter() {
            return balanceAfter;
        }

        public String getDescription() {
            return description;
        }
    }
}
