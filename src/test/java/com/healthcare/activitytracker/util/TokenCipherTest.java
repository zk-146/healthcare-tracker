package com.healthcare.activitytracker.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.config.GoogleHealthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenCipherTest {

  private TokenCipher cipher;

  @BeforeEach
  void setUp() {
    GoogleHealthProperties properties = new GoogleHealthProperties();
    properties.setTokenEncryptionKey("unit-test-passphrase");
    cipher = new TokenCipher(properties);
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
}
