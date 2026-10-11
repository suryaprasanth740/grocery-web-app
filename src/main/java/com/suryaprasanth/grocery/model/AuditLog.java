package com.suryaprasanth.grocery.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One row per sensitive admin action (approving a shop, suspending an account, changing an
 * order's status...). Rows are only ever added, never edited, so there is a trail of who did what.
 * The actor's email is copied in, so the record stays readable even if the account changes later.
 */
@Entity
@Table(name = "audit_logs", indexes = @Index(name = "idx_audit_created", columnList = "created_at"))
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    /** e.g. SHOP_APPROVED, ACCOUNT_SUSPENDED, ORDER_STATUS_CHANGED */
    @Column(nullable = false, length = 40)
    private String action;

    /** e.g. SHOP, USER, ORDER, PRODUCT, COUPON */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(length = 500)
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AuditLog() {
    }

    public AuditLog(User actor, String action, String targetType, Long targetId, String details) {
        this.actorId = actor == null ? null : actor.getId();
        this.actorEmail = actor == null ? "system" : actor.getEmail();
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details == null ? null : (details.length() > 500 ? details.substring(0, 500) : details);
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
