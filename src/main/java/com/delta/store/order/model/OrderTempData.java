package com.delta.store.order.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Contiene i dati temporanei del checkout
 * usati per creare l'ordine definitivo.
 */
public class OrderTempData {

    // ===== Session / User =====
   
	 
	private String sessionId;
    private Long userId;

    // ===== Cliente =====
    private String customerEmail;
    private String customerPhone;
    private String customerName;

    // ===== Spedizione =====
    private String shippingAddress1;
    private String shippingZip;
    private String shippingCity;
    private String shippingProvince;
    private String shippingCountry;

    // ===== Totali =====
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal shippingCost = BigDecimal.ZERO;
    private BigDecimal discountPercent = BigDecimal.ZERO;
    private BigDecimal discountValue = BigDecimal.ZERO;
    private BigDecimal netSubtotal = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // ===== Pagamento =====
    private String paymentMethod;
    private String checkoutAttemptId;
    private String cartFingerprint;
    private boolean stockReserved;
    public String getCheckoutAttemptId() { return checkoutAttemptId; }
    public void setCheckoutAttemptId(String value) { checkoutAttemptId = value; }
    public String getCartFingerprint() { return cartFingerprint; }
    public void setCartFingerprint(String value) { cartFingerprint = value; }
    public boolean isStockReserved() { return stockReserved; }
    public void setStockReserved(boolean value) { stockReserved = value; }

    // ===== Tipo ordine =====
    private String type = "WEB";

    
    
    // ===== GiftCard =====
    private String giftCardCode;
    private BigDecimal giftCardAmount = BigDecimal.ZERO;

    // ===== Carrello =====
    private List<CartItemDTO> cartItems = new ArrayList<>();

    // ==========================
    // GETTER & SETTER
    // ==========================

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getShippingAddress1() { return shippingAddress1; }
    public void setShippingAddress1(String shippingAddress1) { this.shippingAddress1 = shippingAddress1; }

    public String getShippingZip() { return shippingZip; }
    public void setShippingZip(String shippingZip) { this.shippingZip = shippingZip; }

    public String getShippingCity() { return shippingCity; }
    public void setShippingCity(String shippingCity) { this.shippingCity = shippingCity; }

    public String getShippingProvince() { return shippingProvince; }
    public void setShippingProvince(String shippingProvince) { this.shippingProvince = shippingProvince; }

    public String getShippingCountry() { return shippingCountry; }
    public void setShippingCountry(String shippingCountry) { this.shippingCountry = shippingCountry; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getShippingCost() { return shippingCost; }
    public void setShippingCost(BigDecimal shippingCost) { this.shippingCost = shippingCost; }

    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }

    public BigDecimal getNetSubtotal() { return netSubtotal; }
    public void setNetSubtotal(BigDecimal netSubtotal) { this.netSubtotal = netSubtotal; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getGiftCardCode() { return giftCardCode; }
    public void setGiftCardCode(String giftCardCode) { this.giftCardCode = giftCardCode; }

    public BigDecimal getGiftCardAmount() { return giftCardAmount; }
    public void setGiftCardAmount(BigDecimal giftCardAmount) { this.giftCardAmount = giftCardAmount; }

    public List<CartItemDTO> getCartItems() { return cartItems; }
    public void setCartItems(List<CartItemDTO> cartItems) { this.cartItems = cartItems; }
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
 
}
