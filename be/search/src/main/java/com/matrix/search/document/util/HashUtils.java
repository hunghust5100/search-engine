package com.matrix.search.document.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for SHA-256 cryptographic hashing to detect duplicate document contents.
 */
public final class HashUtils {

    private static final String SHA_256 = "SHA-256";

    private HashUtils() {
    }

    /**
     * Compute 64-character lowercase hexadecimal SHA-256 hash of the input string.
     * Null input is treated as empty string.
     *
     * @param input string to hash
     * @return 64-character lowercase hex string
     */
    public static String sha256Hex(String input) {
        String text = input != null ? input : "";
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not supported by JVM", e);
        }
    }

    /**
     * Check if the input text matches an expected SHA-256 hash.
     *
     * @param input text to verify
     * @param expectedHash expected 64-char hex hash
     * @return true if match
     */
    public static boolean matches(String input, String expectedHash) {
        if (expectedHash == null) {
            return false;
        }
        return sha256Hex(input).equalsIgnoreCase(expectedHash.trim());
    }
}
