package com.suryaprasanth.grocery.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.suryaprasanth.grocery.util.EmailUtil;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /**
     * The email in one standard form (see EmailUtil). Unique, so one Gmail inbox can only
     * have one account, even as "s.urya@gmail.com" or "surya+2@gmail.com".
     * Null only for old rows that clash with another account (see DataInitializer).
     */
    @JsonIgnore
    @Column(name = "email_key", unique = true, length = 150)
    private String emailKey;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    /** BUYER, SELLER or ADMIN (see {@link Role}). Old FreshCart rows may still say "USER" = buyer. */
    @Column(nullable = false, length = 10)
    private String role = Role.BUYER.name();

    /** Optional phone number, e.g. "9876543210". Sellers must give one. */
    @Column(length = 15)
    private String phone;

    /** ACTIVE or SUSPENDED. Null on accounts made before this column existed = ACTIVE. */
    @Column(name = "account_status", length = 12)
    private String accountStatus = AccountStatus.ACTIVE.name();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Budget mode: how much the customer wants to spend on groceries per month. Null = not set. */
    @JsonIgnore
    @Column(name = "monthly_budget", precision = 10, scale = 2)
    private BigDecimal monthlyBudget;

    public User() {
    }

    public User(String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.emailKey = EmailUtil.canonical(email);
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
        this.emailKey = EmailUtil.canonical(email);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmailKey() {
        return emailKey;
    }

    public void setEmailKey(String emailKey) {
        this.emailKey = emailKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** The role as text for the API, always BUYER, SELLER or ADMIN (never the old "USER"). */
    public String getRole() {
        return getRoleType().name();
    }

    @JsonIgnore
    public Role getRoleType() {
        return Role.from(role);
    }

    public void setRole(String role) {
        this.role = Role.from(role).name();
    }

    public void setRole(Role role) {
        this.role = role.name();
    }

    public boolean hasRole(Role wanted) {
        return getRoleType() == wanted;
    }

    @JsonIgnore
    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @JsonIgnore
    public AccountStatus getAccountStatus() {
        return accountStatus == null ? AccountStatus.ACTIVE : AccountStatus.valueOf(accountStatus);
    }

    public void setAccountStatus(AccountStatus status) {
        this.accountStatus = status.name();
    }

    @JsonIgnore
    public boolean isSuspended() {
        return getAccountStatus() == AccountStatus.SUSPENDED;
    }

    public BigDecimal getMonthlyBudget() {
        return monthlyBudget;
    }

    public void setMonthlyBudget(BigDecimal monthlyBudget) {
        this.monthlyBudget = monthlyBudget;
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }
}
