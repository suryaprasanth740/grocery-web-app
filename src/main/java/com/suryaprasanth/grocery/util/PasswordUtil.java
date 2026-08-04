package com.suryaprasanth.grocery.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Simple salted-hash password utility.
 * Stored format: "<base64-salt>:<base64-hash>"
 *
 * This avoids pulling in a full security framework for a student project,
 * while still never storing plain-text passwords.
 */
public final class PasswordUtil {

    private static final int SALT_LENGTH_BYTES = 16;

    private PasswordUtil() {
    }

    public static String hash(String plainPassword) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = digest(plainPassword, salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean matches(String plainPassword, String storedHash) {
        if (storedHash == null || !storedHash.contains(":")) {
            return false;
        }
        String[] parts = storedHash.split(":", 2);
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
        byte[] actualHash = digest(plainPassword, salt);
        return MessageDigest.isEqual(expectedHash, actualHash);
    }

    private static byte[] digest(String plainPassword, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            // Run multiple rounds to slow down brute force a little.
            byte[] result = md.digest(plainPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
