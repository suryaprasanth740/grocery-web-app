package com.suryaprasanth.grocery.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Product name/price are copied at checkout time so the order history
    // stays accurate even if the product is later changed or removed.
    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    /** Which product this came from, so stock can be returned if the order is cancelled. */
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "gst_percent")
    private Integer gstPercent = 0;

    public OrderItem() {
    }

    public OrderItem(Order order, String productName, BigDecimal price, Integer quantity) {
        this.order = order;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    public OrderItem(Order order, Product product, Integer quantity) {
        this(order, product.getName(), product.getPrice(), quantity);
        this.productId = product.getId();
        this.gstPercent = product.getGstPercent();
    }

    public BigDecimal lineTotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getGstPercent() {
        return gstPercent == null ? 0 : gstPercent;
    }

    public void setGstPercent(Integer gstPercent) {
        this.gstPercent = gstPercent;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
