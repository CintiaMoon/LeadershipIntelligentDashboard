package com.company.leadershipintelligence.service.identity;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashes and checks passwords (decision D65). bcrypt reads only the first 72 bytes of its input, so the password is first
 * reduced to a fixed-length SHA-256 digest; a long passphrase then counts in full and never meets that limit. The stored value
 * carries the algorithm prefix ({@code {bcrypt}...}) so the algorithm can change later without forcing a reset.
 */
@Component
public class PasswordHasher {

    private final PasswordEncoder encoder;
    private final String dummyHash;

    public PasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
        byte[] noise = new byte[24];
        new SecureRandom().nextBytes(noise);
        this.dummyHash = encoder.encode(digest(Base64.getEncoder().encodeToString(noise)));
    }

    public String hash(String password) {
        return encoder.encode(digest(password));
    }

    public boolean matches(String password, String storedHash) {
        if (password == null || storedHash == null) {
            return false;
        }
        try {
            return encoder.matches(digest(password), storedHash);
        } catch (IllegalArgumentException e) {
            // a stored value without a known algorithm prefix is a bad row; it fails like a wrong password, not as a 500
            return false;
        }
    }

    /** Does the same work as a real check, for a sign-in with an unknown email, so the answer takes about as long. */
    public void spendTime(String password) {
        encoder.matches(digest(password == null ? "" : password), dummyHash);
    }

    private static String digest(String password) {
        try {
            byte[] sum = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sum);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
