package com.suryaprasanth.grocery.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A photo uploaded by a seller (product photo or shop logo), stored in the database.
 *
 * Why the database and not the server's disk: the free Render host wipes its disk on every
 * deploy, so files saved there would disappear. Photos are resized in the browser first and
 * limited to 300 KB, so they stay small.
 */
@Entity
@Table(name = "stored_images", indexes = @Index(name = "idx_image_shop", columnList = "shop_id"))
public class StoredImage {

    public static final int MAX_BYTES = 300 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The shop that uploaded it. Only that shop can attach it to its products or logo. */
    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, length = MAX_BYTES)
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public StoredImage() {
    }

    public StoredImage(Long shopId, String contentType, byte[] data) {
        this.shopId = shopId;
        this.contentType = contentType;
        this.data = data;
        this.sizeBytes = data.length;
    }

    /** The address pages use to show it, e.g. "/api/images/42". */
    public String getUrl() { return "/api/images/" + id; }

    public Long getId() { return id; }
    public Long getShopId() { return shopId; }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public byte[] getData() { return data; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
