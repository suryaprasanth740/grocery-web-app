package com.suryaprasanth.grocery.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A buyer's report about a shop, or about one product of a shop. Admins investigate and close it.
 * The shop is always set (a product report also points to the product's shop), so admins can see
 * how many reports a shop collects in total.
 */
@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_report_status", columnList = "status"),
        @Index(name = "idx_report_shop", columnList = "shop_id")
})
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    /** Null when the report is about the shop itself. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = 500)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "resolution_note", length = 300)
    private String resolutionNote;

    @Column(name = "resolved_by", length = 150)
    private String resolvedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public Report() {
    }

    @JsonProperty("targetType")
    public String targetType() { return product == null ? "SHOP" : "PRODUCT"; }

    @JsonProperty("shopId")
    public Long shopId() { return shop.getId(); }

    @JsonProperty("shopName")
    public String shopName() { return shop.getName(); }

    @JsonProperty("shopStatus")
    public String shopStatus() { return shop.getStatus().name(); }

    @JsonProperty("productId")
    public Long productId() { return product == null ? null : product.getId(); }

    @JsonProperty("productName")
    public String productName() { return product == null ? null : product.getName(); }

    @JsonProperty("productActive")
    public Boolean productActive() { return product == null ? null : product.isActive(); }

    @JsonProperty("reasonLabel")
    public String reasonLabel() { return reason.getLabel(); }

    @JsonProperty("reporterEmail")
    public String reporterEmail() { return reporter.getEmail(); }

    public Long getId() { return id; }
    public User getReporter() { return reporter; }
    public void setReporter(User reporter) { this.reporter = reporter; }
    public Shop getShop() { return shop; }
    public void setShop(Shop shop) { this.shop = shop; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public ReportReason getReason() { return reason; }
    public void setReason(ReportReason reason) { this.reason = reason; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
