package com.healthcare.activitytracker.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Helpers for generating and hashing opaque tokens (refresh tokens, email-verification and
 * password-reset tokens). Raw tokens are delivered to the user; only their SHA-256 hash is
 * persisted, limiting exposure if the database is compromised.
 */
public final class TokenHasher {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int RAW_TOKEN_BYTES = 32;

  private TokenHasher() {}

  /** Generates a cryptographically random, URL-safe token (256 bits of entropy, no padding). */
  public static String generateRawToken() {
    byte[] bytes = new byte[RAW_TOKEN_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Lowercase hex SHA-256 of the supplied input. */
  public static String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
