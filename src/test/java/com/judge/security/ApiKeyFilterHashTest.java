package com.judge.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyFilterHashTest {

    @Test
    void sha256HexMatchesKnownVector() {
        // echo -n "abc" | sha256sum
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ApiKeyFilter.sha256Hex("abc"));
    }

    @Test
    void hashIsDeterministicAndSized() {
        String h1 = ApiKeyFilter.sha256Hex("sk_deadbeef");
        String h2 = ApiKeyFilter.sha256Hex("sk_deadbeef");
        assertEquals(h1, h2);
        assertEquals(64, h1.length());
    }

    @Test
    void differentKeysHashDifferently() {
        assertNotEquals(ApiKeyFilter.sha256Hex("sk_a"), ApiKeyFilter.sha256Hex("sk_b"));
    }
}
