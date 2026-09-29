package com.suryaprasanth.grocery.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.suryaprasanth.grocery.util.EmailUtil;
import jakarta.persistence.*;
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

    /** "USER" for customers, "ADMIN" for staff who can use the admin panel. */
    @Column(nullable = false, length = 10)
    private String role = "USER";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
