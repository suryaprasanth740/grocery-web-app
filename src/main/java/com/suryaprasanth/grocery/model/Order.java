package com.suryaprasanth.grocery.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", uniqueConstraints = {
        // Duplicate-order protection: one order per (user, checkout request id).
        // A double click sends the same request id twice; the database refuses the second one.
        @UniqueConstraint(name = "uk_order_user_request", columnNames = {"user_id", "request_id"})
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Optimistic locking: if two actions change the same order at the same moment
     * (e.g. the payment time-out job and a late payment, or admin "packed" and customer
     * "cancel"), the second one fails instead of silently overwriting the first.
     */
    @JsonIgnore
    @Version
    private Long version;

    /** Random id created by the checkout page, used to detect duplicate submits. */
    @JsonIgnore
    @Column(name = "request_id", length = 64)
    private String requestId;

    // ---- Bill breakdown (all amounts in rupees) ----
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    /** GST that is already included in the item prices (shown for transparency). */
    @Column(name = "gst_included", nullable = false, precision = 10, scale = 2)
    private BigDecimal gstIncluded = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "coupon_code", length = 30)
    private String couponCode;

    // ---- Status ----
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PLACED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.COD_PENDING;

    /** Why a payment failed / an order was cancelled, shown to the customer. */
    @Column(name = "status_note", length = 250)
    private String statusNote;

    // ---- Delivery ----
    @Column(name = "shipping_address", length = 300)
    private String shippingAddress;

    @Column(length = 6)
    private String pincode;

    @Enumerated(EnumType.STRING)
    @Column(name = "substitution_preference", length = 20)
    private SubstitutionPreference substitutionPreference = SubstitutionPreference.REFUND_ITEM;

    // ---- Timestamps ----
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<OrderIssue> issues = new ArrayList<>();

    public Order() {
    }

    /** Shown in the admin panel so staff know whose order it is. */
    @JsonProperty("customerName")
    public String customerName() {
        return user != null ? user.getName() : null;
    }

    @JsonProperty("customerEmail")
    public String customerEmail() {
        return user != null ? user.getEmail() : null;
    }

    /** Total refund approved for reported problems (missing / damaged items). */
    @JsonProperty("issueRefundTotal")
    public BigDecimal issueRefundTotal() {
        return issues.stream().map(OrderIssue::getRefundAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }

    public BigDecimal getGstIncluded() { return gstIncluded; }
    public void setGstIncluded(BigDecimal gstIncluded) { this.gstIncluded = gstIncluded; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; touch(); }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; touch(); }

    public String getStatusNote() { return statusNote; }
    public void setStatusNote(String statusNote) { this.statusNote = statusNote; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public SubstitutionPreference getSubstitutionPreference() { return substitutionPreference; }
    public void setSubstitutionPreference(SubstitutionPreference substitutionPreference) {
        this.substitutionPreference = substitutionPreference;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public List<OrderIssue> getIssues() { return issues; }
    public void setIssues(List<OrderIssue> issues) { this.issues = issues; }
}
