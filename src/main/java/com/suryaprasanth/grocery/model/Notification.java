package com.suryaprasanth.grocery.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A short in-app message, e.g. "Order #12 is out for delivery". Shown under the bell in the header. */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notification_user", columnList = "user_id, created_at"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 300)
    private String body;

    /** Page to open when the notification is clicked, e.g. "/order.html?id=12". */
    @Column(length = 200)
    private String link;

    @Column(name = "is_read")
    private Boolean read = Boolean.FALSE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification() {
    }

    public Notification(User user, String title, String body, String link) {
        this.user = user;
        this.title = cut(title, 120);
        this.body = cut(body, 300);
        this.link = cut(link, 200);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public boolean isRead() { return read != null && read; }
    public void setRead(boolean read) { this.read = read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
