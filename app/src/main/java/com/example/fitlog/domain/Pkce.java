package com.example.fitlog.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** OAuth 2.0 PKCE (RFC 7636) yardımcıları. */
public final class Pkce {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Pkce() {}

    /** 43-128 karakterlik, URL güvenli rastgele code_verifier. */
    public static String newVerifier() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return base64Url(bytes);
    }

    /** S256 code_challenge = BASE64URL(SHA256(verifier)). */
    public static String challenge(String verifier) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return base64Url(sha.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
