package com.healthcare.activitytracker.util;

import com.healthcare.activitytracker.config.GoogleHealthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts and decrypts OAuth tokens before they are persisted, so a database leak does not expose
 * usable Google credentials.
 *
 * <p>Uses AES-256-GCM. The 256-bit key is derived from the configured passphrase via SHA-256, so
 * any passphrase length is accepted. A fresh random 96-bit IV is generated per encryption and
 * prepended to the ciphertext; the whole blob is Base64-encoded for storage in a TEXT column.
 */
@Component
public class TokenCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom secureRandom = new SecureRandom();

  public TokenCipher(GoogleHealthProperties properties) {
    this.key = deriveKey(properties.getTokenEncryptionKey());
  }

  private static SecretKeySpec deriveKey(String passphrase) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      byte[] keyBytes = sha256.digest(passphrase.getBytes(StandardCharsets.UTF_8));
      return new SecretKeySpec(keyBytes, "AES");
    } catch (Exception e) {
      throw new IllegalStateException("Unable to derive token encryption key", e);
    }
  }

  /** Encrypts plaintext and returns a Base64 string of {@code IV || ciphertext}. */
  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      byte[] combined = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
      return Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException("Token encryption failed", e);
    }
  }

  /** Reverses {@link #encrypt(String)}. */
  public String decrypt(String encrypted) {
    try {
      byte[] combined = Base64.getDecoder().decode(encrypted);
      byte[] iv = new byte[IV_LENGTH_BYTES];
      System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] plaintext =
          cipher.doFinal(combined, IV_LENGTH_BYTES, combined.length - IV_LENGTH_BYTES);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Token decryption failed", e);
    }
  }
}
