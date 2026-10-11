package com.suryaprasanth.grocery.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing.
 *
 * New passwords are hashed with BCrypt (Spring Security's standard encoder). A BCrypt hash
 * starts with "$2" and contains its own random salt, e.g. "$2a$10$N9qo8uLOickgx2ZMRZoMye...".
 *
 * Accounts created by the old FreshCart code have a "salt:hash" SHA-256 value instead. Those
 * still log in, and {@link #needsUpgrade(String)} tells the login code to replace the old hash
 * with a BCrypt one, so every active account moves to BCrypt without anyone resetting a password.
 */
public final class PasswordUtil {

    /** BCrypt works on at most 72 bytes; longer passwords are refused at sign-up. */
    public static final int MAX_PASSWORD_BYTES = 72;

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);

    /** A real BCrypt hash of a random value, used so "no such user" takes as long as "wrong password". */
    private static final String DUMMY_HASH = BCRYPT.encode("not-a-real-password-" + new SecureRandom().nextLong());

    private PasswordUtil() {
    }

    public static String hash(String plainPassword) {
        return BCRYPT.encode(plainPassword);
    }

    public static boolean matches(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (isBcrypt(storedHash)) {
            return BCRYPT.matches(plainPassword, storedHash);
        }
        return legacyMatches(plainPassword, storedHash);
    }

    /** True for old SHA-256 hashes that should be re-hashed with BCrypt after a successful login. */
    public static boolean needsUpgrade(String storedHash) {
        return storedHash != null && !isBcrypt(storedHash);
    }

    /** Spends the same time as a real check. Called when the email is unknown. */
    public static void burnTime(String plainPassword) {
        BCRYPT.matches(plainPassword == null ? "" : plainPassword, DUMMY_HASH);
    }

    public static boolean tooLong(String plainPassword) {
        return plainPassword != null && plainPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }

    private static boolean isBcrypt(String storedHash) {
        return storedHash.startsWith("$2");
    }

    // ---------- Old FreshCart format, kept only so existing accounts can still log in ----------

    private static final int LEGACY_SALT_BYTES = 16;

    /** Creates a hash in the OLD format. Used only by tests that check the upgrade to BCrypt. */
    public static String legacyHash(String plainPassword) {
        byte[] salt = new byte[LEGACY_SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = legacyDigest(plainPassword, salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    private static boolean legacyMatches(String plainPassword, String storedHash) {
        if (!storedHash.contains(":")) {
            return false;
        }
        try {
            String[] parts = storedHash.split(":", 2);
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
            return MessageDigest.isEqual(expectedHash, legacyDigest(plainPassword, salt));
        } catch (IllegalArgumentException badBase64) {
            return false;
        }
    }

    private static byte[] legacyDigest(String plainPassword, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] result = md.digest(plainPassword.getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < 10_000; i++) {
                md.reset();
                result = md.digest(result);
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
