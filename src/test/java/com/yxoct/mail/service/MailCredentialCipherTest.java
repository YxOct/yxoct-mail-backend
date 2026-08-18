package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yxoct.mail.config.StalwartProvisioningProperties;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class MailCredentialCipherTest {

  private final MailCredentialCipher cipher =
      new MailCredentialCipher(properties(), new SecureRandom());

  @Test
  void encryptsWithRandomNoncesAndDecryptsCredentials() {
    String first = cipher.encrypt("mail-secret");
    String second = cipher.encrypt("mail-secret");

    assertThat(first).startsWith("v1:").isNotEqualTo(second).doesNotContain("mail-secret");
    assertThat(cipher.decrypt(first)).isEqualTo("mail-secret");
    assertThat(cipher.decrypt(second)).isEqualTo("mail-secret");
  }

  @Test
  void rejectsTamperedCiphertext() {
    String encrypted = cipher.encrypt("mail-secret");
    char replacement = encrypted.endsWith("A") ? 'B' : 'A';
    String tampered = encrypted.substring(0, encrypted.length() - 1) + replacement;

    assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
  }

  private StalwartProvisioningProperties properties() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return new StalwartProvisioningProperties(
        true,
        "API-key",
        Base64.getUrlEncoder().withoutPadding().encodeToString(key),
        Duration.ofSeconds(10),
        Duration.ofMinutes(1),
        Duration.ofSeconds(30),
        Duration.ofHours(1),
        20);
  }
}
