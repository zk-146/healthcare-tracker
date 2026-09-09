package com.healthcare.activitytracker.util;

import com.healthcare.activitytracker.config.GoogleHealthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Encrypts and decrypts OAuth tokens before they are persisted, so a database leak does not expose
 * usable Google credentials.
 *
 * <p>Uses AES-256-GCM. A fresh random 96-bit IV is generated per encryption and prepended to the
 * ciphertext; the whole blob is Base64-encoded for storage in a TEXT column.
 *
 * <p>The 256-bit key comes from {@code app.integrations.google-health.token-encryption-key} in one
 * of two ways:
 *
 * <ul>
 *   <li><strong>Preferred:</strong> a base64-encoded 32 random bytes ({@code openssl rand -base64
 *       32}) is used as the AES key directly. A random key needs no stretching.
 *   <li><strong>Fallback:</strong> anything else is treated as a human-chosen passphrase and
 *       stretched with PBKDF2-HMAC-SHA256. A passphrase has far less entropy than the key it
 *       produces, so the work factor is what stands between a database leak and an offline brute
 *       force.
 * </ul>
 */
@Component
public class TokenCipher {

  private static final Logger log = LoggerFactory.getLogger(TokenCipher.class);

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;
  private static final int AES_KEY_LENGTH_BYTES = 32;

  /** OWASP's 2023 guidance for PBKDF2-HMAC-SHA256. Paid once, at construction. */
  private static final int PBKDF2_ITERATIONS = 210_000;

  /**
   * A salt stops an attacker precomputing one table that cracks many hashes at once. This
   * deployment derives exactly one key from one passphrase, so there is nothing to amortise across
   * and a constant is sufficient; the iteration count above is what supplies the work factor. It is
   * deliberately not secret -- salts never are.
   */
  private static final byte[] PBKDF2_SALT =
      "activity-tracker/google-health/token-encryption".getBytes(StandardCharsets.UTF_8);

  private final SecretKeySpec key;

  /**
   * The key produced by the original single-pass SHA-256 derivation, retained only so tokens
   * encrypted before that scheme was replaced can still be read. Rows re-encrypt themselves on the
   * next write (access tokens refresh roughly hourly), so this field and the fallback in {@link
   * #decrypt} can be deleted once no deployment holds pre-migration rows.
   */
  private final SecretKeySpec legacyKey;

  private final SecureRandom secureRandom = new SecureRandom();

  public TokenCipher(GoogleHealthProperties properties) {
    String configured = properties.getTokenEncryptionKey();
    this.key = deriveKey(configured);
    this.legacyKey = legacyDeriveKey(configured);
  }

  private static SecretKeySpec deriveKey(String configured) {
    byte[] decoded = tryDecodeBase64Key(configured);
    if (decoded != null) {
      return new SecretKeySpec(decoded, "AES");
    }
    log.warn(
        "token-encryption-key is not a base64-encoded {}-byte key, so it is being stretched as a "
            + "passphrase. Prefer a real key: openssl rand -base64 {}",
        AES_KEY_LENGTH_BYTES,
        AES_KEY_LENGTH_BYTES);
    try {
      KeySpec spec =
          new PBEKeySpec(
              configured.toCharArray(),
              PBKDF2_SALT,
              PBKDF2_ITERATIONS,
              AES_KEY_LENGTH_BYTES * Byte.SIZE);
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    } catch (Exception e) {
      throw new IllegalStateException("Unable to derive token encryption key", e);
    }
  }

  /** Returns the decoded bytes only when the value is base64 for exactly an AES-256 key. */
  private static byte[] tryDecodeBase64Key(String configured) {
    try {
      byte[] decoded = Base64.getDecoder().decode(configured.trim());
      return decoded.length == AES_KEY_LENGTH_BYTES ? decoded : null;
    } catch (IllegalArgumentException notBase64) {
      return null;
    }
  }

  /** The superseded derivation: a single unsalted SHA-256 pass over the passphrase. */
  private static SecretKeySpec legacyDeriveKey(String passphrase) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      return new SecretKeySpec(sha256.digest(passphrase.getBytes(StandardCharsets.UTF_8)), "AES");
    } catch (Exception e) {
      throw new IllegalStateException("Unable to derive legacy token encryption key", e);
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

  /**
   * Reverses {@link #encrypt(String)}.
   *
   * <p>A blob written before the key derivation changed will not authenticate under the current
   * key, so it is retried under the legacy one. GCM's authentication tag makes that unambiguous --
   * the wrong key always fails the tag, so a successful legacy decrypt cannot be a false positive.
   */
  public String decrypt(String encrypted) {
    byte[] combined = Base64.getDecoder().decode(encrypted);
    try {
      return decryptWith(combined, key);
    } catch (AEADBadTagException currentKeyDidNotAuthenticate) {
      try {
        String plaintext = decryptWith(combined, legacyKey);
        log.info(
            "Decrypted an OAuth token written under the superseded key derivation; it will be "
                + "re-encrypted under the current key on the next write.");
        return plaintext;
      } catch (Exception e) {
        throw new IllegalStateException("Token decryption failed", e);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Token decryption failed", e);
    }
  }

  private String decryptWith(byte[] combined, SecretKeySpec withKey) throws Exception {
    byte[] iv = new byte[IV_LENGTH_BYTES];
    System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);

    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(Cipher.DECRYPT_MODE, withKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
    byte[] plaintext = cipher.doFinal(combined, IV_LENGTH_BYTES, combined.length - IV_LENGTH_BYTES);
    return new String(plaintext, StandardCharsets.UTF_8);
  }
}
