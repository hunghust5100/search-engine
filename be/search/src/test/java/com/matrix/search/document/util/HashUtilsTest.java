package com.matrix.search.document.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashUtilsTest {

    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void hashesEmptyAndNullString() {
        assertEquals(EMPTY_SHA256, HashUtils.sha256Hex(""));
        assertEquals(EMPTY_SHA256, HashUtils.sha256Hex(null));
    }

    @Test
    void hashesUtf8VietnameseTextConsistently() {
        String text = "Đại học Bách khoa Hà Nội - Số 1 Đại Cồ Việt";
        String hash1 = HashUtils.sha256Hex(text);
        String hash2 = HashUtils.sha256Hex(text);

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
    }

    @Test
    void differentTextYieldsDifferentHash() {
        String textA = "Thông báo tuyển sinh 2026";
        String textB = "Thông báo tuyển sinh 2027";

        String hashA = HashUtils.sha256Hex(textA);
        String hashB = HashUtils.sha256Hex(textB);

        assertNotEquals(hashA, hashB);
    }

    @Test
    void verifiesMatchesMethod() {
        String text = "HUST Search Engine";
        String hash = HashUtils.sha256Hex(text);

        assertTrue(HashUtils.matches(text, hash));
        assertTrue(HashUtils.matches(text, hash.toUpperCase()));
        assertFalse(HashUtils.matches(text, "invalidhash"));
        assertFalse(HashUtils.matches(text, null));
    }
}
