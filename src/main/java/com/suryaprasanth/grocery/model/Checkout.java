package com.suryaprasanth.grocery.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One press of "Place order". A basket can hold items from several shops, and each shop packs
 * its own items, so one checkout creates one {@link Order} per shop. The checkout keeps what
 * belongs to the whole press: the request id (double-click protection), the grand total and
 * the payment method (one demo UPI payment covers all the shops).
 */
@Entity
@Table(name = "checkouts", uniqueConstraints =
        @UniqueConstraint(name = "uk_checkout_user_request", columnNames = {"user_id", "request_id"}))
public class Checkout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Random id made by the checkout page. The same id twice returns the same checkout. */
    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "checkout")
    @OrderBy("id ASC")
    private List<Order> orders = new ArrayList<>();

    public Checkout() {
    }

    @JsonProperty("checkoutId")
    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<Order> getOrders() { return orders; }
}
