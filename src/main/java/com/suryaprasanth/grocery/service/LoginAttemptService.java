package com.suryaprasanth.grocery.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection: after too many wrong passwords for one email, that email is
 * locked for some minutes. Kept in memory (resets on restart) - fine for this project;
 * a big site would use Redis so all servers share the counts.
 */
@Service
public class LoginAttemptService {

    private record Attempts(int failures, LocalDateTime lockedUntil) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final int lockMinutes;

    public LoginAttemptService(@Value("${app.login.max-attempts:5}") int maxAttempts,
                               @Value("${app.login.lock-minutes:15}") int lockMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
    }

    /** Minutes left on the lock, or 0 if the account is not locked. */
    public long minutesLocked(String email) {
        Attempts a = attempts.get(key(email));
        if (a == null || a.lockedUntil() == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(a.lockedUntil())) {
            attempts.remove(key(email));
            return 0;
        }
        return Math.max(1, java.time.Duration.between(now, a.lockedUntil()).toMinutes() + 1);
    }

    public void loginFailed(String email) {
        attempts.compute(key(email), (k, old) -> {
            int failures = (old == null ? 0 : old.failures()) + 1;
            LocalDateTime lockedUntil = failures >= maxAttempts ? LocalDateTime.now().plusMinutes(lockMinutes) : null;
            return new Attempts(failures, lockedUntil);
        });
    }

    public void loginSucceeded(String email) {
        attempts.remove(key(email));
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
