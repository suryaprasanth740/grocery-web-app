package com.suryaprasanth.grocery.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "cart_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "product_id"})
})
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    /** Price when the item was put in the cart, so we can warn "price changed". */
    @Column(name = "price_when_added", precision = 10, scale = 2)
    private BigDecimal priceWhenAdded;

    public CartItem() {
    }

    public CartItem(User user, Product product, Integer quantity) {
        this.user = user;
        this.product = product;
        this.quantity = quantity;
        this.priceWhenAdded = product != null ? product.getPrice() : null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPriceWhenAdded() {
        return priceWhenAdded;
    }

    public void setPriceWhenAdded(BigDecimal priceWhenAdded) {
        this.priceWhenAdded = priceWhenAdded;
    }

    /** True when the product price is different from the price the customer saw. */
    public boolean isPriceChanged() {
        return priceWhenAdded != null && product != null
                && priceWhenAdded.compareTo(product.getPrice()) != 0;
    }
}
