package com.healthcare.activitytracker.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthcare.activitytracker.config.GoogleHealthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenCipherTest {

  /** A base64-encoded 32 bytes -- the form the cipher uses as an AES key directly. */
  private static final String BASE64_KEY = Base64.getEncoder().encodeToString(new byte[32]);

  private TokenCipher cipher;

  private static TokenCipher cipherWithKey(String tokenEncryptionKey) {
    GoogleHealthProperties properties = new GoogleHealthProperties();
    properties.setTokenEncryptionKey(tokenEncryptionKey);
    return new TokenCipher(properties);
  }

  @BeforeEach
  void setUp() {
    cipher = cipherWithKey("unit-test-passphrase");
  }

  @Test
  void encryptThenDecryptRoundTrips() {
    String secret = "ya29.a0AfH-some-google-access-token";
    String encrypted = cipher.encrypt(secret);

    assertThat(encrypted).isNotEqualTo(secret);
    assertThat(cipher.decrypt(encrypted)).isEqualTo(secret);
  }

  @Test
  void sameInputProducesDifferentCiphertextEachTime() {
    String secret = "refresh-token";
    assertThat(cipher.encrypt(secret)).isNotEqualTo(cipher.encrypt(secret));
  }

  @Test
  void passphraseKeyIsStableAcrossInstances() {
    String encrypted = cipherWithKey("unit-test-passphrase").encrypt("token");
    assertThat(cipherWithKey("unit-test-passphrase").decrypt(encrypted)).isEqualTo("token");
  }

  @Test
  void base64EncodedThirtyTwoByteKeyIsUsedAsTheAesKeyDirectly() throws Exception {
    // Encrypted here with the decoded bytes as the raw AES key. It only decrypts if the
    // cipher used those bytes directly rather than stretching the string as a passphrase.
    String encrypted =
        encryptWith(new SecretKeySpec(Base64.getDecoder().decode(BASE64_KEY), "AES"), "token");

    assertThat(cipherWithKey(BASE64_KEY).decrypt(encrypted)).isEqualTo("token");
  }

  @Test
  void readsTokensWrittenUnderTheSupersededSha256Derivation() throws Exception {
    String passphrase = "unit-test-passphrase";
    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    SecretKeySpec legacyKey =
        new SecretKeySpec(sha256.digest(passphrase.getBytes(StandardCharsets.UTF_8)), "AES");

    String legacyBlob = encryptWith(legacyKey, "pre-migration-refresh-token");

    assertThat(cipherWithKey(passphrase).decrypt(legacyBlob))
        .isEqualTo("pre-migration-refresh-token");
  }

  @Test
  void rejectsCiphertextFromAnotherKey() {
    String encrypted = cipherWithKey("one-passphrase").encrypt("token");

    assertThatThrownBy(() -> cipherWithKey("a-different-passphrase").decrypt(encrypted))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("decryption failed");
  }

  /** Mirrors the cipher's on-disk format: Base64(IV || ciphertext), AES-256-GCM. */
  private static String encryptWith(SecretKeySpec key, String plaintext) throws Exception {
    byte[] iv = new byte[12];
    new SecureRandom().nextBytes(iv);

    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
    byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

    byte[] combined = new byte[iv.length + ciphertext.length];
    System.arraycopy(iv, 0, combined, 0, iv.length);
    System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
    return Base64.getEncoder().encodeToString(combined);
  }
}
