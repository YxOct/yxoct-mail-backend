package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.config.RegistrationProperties;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EmailAddressNormalizerTest {

  private final EmailAddressNormalizer normalizer =
      new EmailAddressNormalizer(
          new RegistrationProperties(
              "yxoct.com", Duration.ofDays(7), Set.of("billing", "notifications")));

  @Test
  void alwaysRejectsCoreReservedLocalParts() {
    assertUnavailable("owner");
    assertUnavailable("OWNER");
    assertUnavailable("postmaster");
    assertUnavailable("security");
  }

  @Test
  void rejectsConfiguredBusinessReservedLocalParts() {
    assertUnavailable("billing");
    assertUnavailable("Notifications");
  }

  @Test
  void allowsNamesThatOnlyContainAReservedWord() {
    assertThat(normalizer.normalize("admin-team")).isEqualTo("admin-team@yxoct.com");
    assertThat(normalizer.normalize("mailbox")).isEqualTo("mailbox@yxoct.com");
  }

  private void assertUnavailable(String localPart) {
    assertThatThrownBy(() -> normalizer.normalize(localPart))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE));
  }
}
