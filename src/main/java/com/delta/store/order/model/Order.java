package com.delta.store.order.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.delta.store.user.model.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_created_date", columnList = "created_date"),
                @Index(name = "idx_orders_customer_email", columnList = "customerEmail"),
                @Index(name = "idx_orders_user_id", columnList = "user_id"),
                @Index(name = "idx_orders_type_order_id", columnList = "type_order_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_orders_stripe_session_id", columnNames = "stripeSessionId")
        }
)
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"items", "user", "typeOrder"})
@EqualsAndHashCode(exclude = {"items", "user", "typeOrder"})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ------------------- Dati cliente -------------------

    private String customerName;
    private String customerEmail;
    private String customerAddress;
    private String customerPhono;

    // Utente registrato (NULL per guest)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id")
    private User user;

    // ------------------- Tipo ordine -------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "type_order_id")
    private OrderType typeOrder;

    // ------------------- Breakdown importi -------------------

    /** Subtotale lordo (somma righe prima dello sconto) */
    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal;

    /** Percentuale sconto (es. 5, 10) */
    @Column(precision = 5, scale = 2)
    private BigDecimal discountPct;

    /** Valore sconto in € */
    @Column(precision = 10, scale = 2)
    private BigDecimal discountValue;

    /** Subtotale dopo sconto (>= 0) */
    @Column(precision = 10, scale = 2)
    private BigDecimal netSubtotal;

    /** Spese di spedizione */
    @Column(precision = 10, scale = 2)
    private BigDecimal shippingCost;

    /** Totale ordine (netSubtotal + shippingCost) – IMPORTO TOTALE ORDINE, non solo incassato gateway */
    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount;

    // ------------------- GiftCard -------------------

    /** Codice GiftCard utilizzata (se presente) */
    @Column(name = "giftcard_code", length = 50)
    private String giftcardCode;

    /** Importo GiftCard utilizzato su questo ordine */
    @Column(name = "giftcard_amount", precision = 10, scale = 2)
    private BigDecimal giftcardAmount;

    // ------------------- Pagamenti / stato -------------------

    /** Id sessione Stripe (se pagamento Stripe) */
    @Column(unique = true, length = 255)
    private String stripeSessionId;

    /** Id generico di sessione pagamento (Stripe o PayPal) */
    private String sessionId;

    /** Stato ordine (es. PAGATO, IN_ATTESA, ANNULLATO, PAGATO CON GIFTCARD, ...) */
    private String status;

    // ------------------- Timestamp -------------------

    /** Timestamp completo creato da Hibernate al persist */
    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /** Data ricavata da createdAt (per indicizzazione/ricerca) */
    @Column(name = "created_date", nullable = false)
    private LocalDate createdDate;

    /** Ora ricavata da createdAt (per indicizzazione/ricerca) */
    @Column(name = "created_time", nullable = false)
    private LocalTime createdTime;

    // ------------------- Relazione con items -------------------

    @OneToMany(
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            mappedBy = "order",
            orphanRemoval = true
    )
    @JsonManagedReference
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 4)
    private Set<OrderItem> items = new HashSet<>();

    // ------------------- Lifecycle hooks -------------------

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (createdDate == null) {
            createdDate = createdAt.toLocalDate();
        }
        if (createdTime == null) {
            createdTime = createdAt.toLocalTime().withSecond(0).withNano(0);
        }
    }

    @PreUpdate
    public void preUpdate() {
        if (createdAt != null) {
            createdDate = createdAt.toLocalDate();
            createdTime = createdAt.toLocalTime().withSecond(0).withNano(0);
        }
    }

    // ------------------- Helper relazione -------------------

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    // ------------------- Getter / Setter -------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }

    public String getCustomerPhono() {
        return customerPhono;
    }

    public void setCustomerPhono(String customerPhono) {
        this.customerPhono = customerPhono;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public OrderType getTypeOrder() {
        return typeOrder;
    }

    public void setTypeOrder(OrderType typeOrder) {
        this.typeOrder = typeOrder;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getDiscountPct() {
        return discountPct;
    }

    public void setDiscountPct(BigDecimal discountPct) {
        this.discountPct = discountPct;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getNetSubtotal() {
        return netSubtotal;
    }

    public void setNetSubtotal(BigDecimal netSubtotal) {
        this.netSubtotal = netSubtotal;
    }

    public BigDecimal getShippingCost() {
        return shippingCost;
    }

    public void setShippingCost(BigDecimal shippingCost) {
        this.shippingCost = shippingCost;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getGiftcardCode() {
        return giftcardCode;
    }

    public void setGiftcardCode(String giftcardCode) {
        this.giftcardCode = giftcardCode;
    }

    public BigDecimal getGiftcardAmount() {
        return giftcardAmount;
    }

    public void setGiftcardAmount(BigDecimal giftcardAmount) {
        this.giftcardAmount = giftcardAmount;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public void setStripeSessionId(String stripeSessionId) {
        this.stripeSessionId = stripeSessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public LocalTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalTime createdTime) {
        this.createdTime = createdTime;
    }

    public Set<OrderItem> getItems() {
        return items;
    }

    public void setItems(Set<OrderItem> items) {
        this.items = items;
    }
}