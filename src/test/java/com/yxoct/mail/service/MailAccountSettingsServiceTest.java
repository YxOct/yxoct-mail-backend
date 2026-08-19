package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.mail.MailAccountSettings;
import com.yxoct.mail.persistence.MailAccountSettingsRepository;
import com.yxoct.mail.persistence.OwnedMailAccount;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailAccountSettingsServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-19T10:00:00Z");
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZONE);

  @Mock private MailAccountSettingsRepository repository;
  @Mock private StalwartManagementClient managementClient;

  private MailAccountSettingsService service;

  @BeforeEach
  void setUp() {
    service =
        new MailAccountSettingsService(
            repository, managementClient, new DisplayNameNormalizer(), Clock.fixed(NOW, ZONE));
  }

  @Test
  void updatesTheOwnedActiveAccountRemotelyAndLocally() {
    when(repository.findOwnedForUpdate(1, 2)).thenReturn(Optional.of(activeAccount("Alice")));
    when(repository.updateDisplayName(2, "Alice Zhang", NOW_LOCAL)).thenReturn(true);

    assertThat(service.updateDisplayName("1", 2, "  Alice Zhang  "))
        .isEqualTo(new MailAccountSettings(2, "Alice Zhang"));

    verify(managementClient).updateAccountDisplayName("stalwart-2", "Alice Zhang");
    verify(repository).updateDisplayName(2, "Alice Zhang", NOW_LOCAL);
  }

  @Test
  void doesNothingWhenTheNormalizedDisplayNameIsUnchanged() {
    when(repository.findOwnedForUpdate(1, 2)).thenReturn(Optional.of(activeAccount("Alice")));

    assertThat(service.updateDisplayName("1", 2, " Alice "))
        .isEqualTo(new MailAccountSettings(2, "Alice"));

    verifyNoInteractions(managementClient);
    verify(repository, never()).updateDisplayName(2, "Alice", NOW_LOCAL);
  }

  @Test
  void hidesAccountsThatAreNotOwnedByTheAuthenticatedUser() {
    when(repository.findOwnedForUpdate(1, 2)).thenReturn(Optional.empty());

    assertBusinessError(
        () -> service.updateDisplayName("1", 2, "Alice"), ErrorCode.RESOURCE_NOT_FOUND);
    verifyNoInteractions(managementClient);
  }

  @Test
  void rejectsAnAccountThatIsNotActive() {
    when(repository.findOwnedForUpdate(1, 2))
        .thenReturn(
            Optional.of(new OwnedMailAccount(2, null, "Alice", MailAccountStatus.PROVISIONING)));

    assertBusinessError(
        () -> service.updateDisplayName("1", 2, "Alice Zhang"), ErrorCode.MAIL_ACCOUNT_NOT_READY);
    verifyNoInteractions(managementClient);
  }

  @Test
  void keepsTheLocalNameWhenStalwartRejectsTheUpdate() {
    when(repository.findOwnedForUpdate(1, 2)).thenReturn(Optional.of(activeAccount("Alice")));
    org.mockito.Mockito.doThrow(new StalwartProvisioningException("ACCOUNT_UPDATE_REJECTED"))
        .when(managementClient)
        .updateAccountDisplayName("stalwart-2", "Alice Zhang");

    assertBusinessError(
        () -> service.updateDisplayName("1", 2, "Alice Zhang"), ErrorCode.MAIL_SERVICE_UNAVAILABLE);
    verify(repository, never()).updateDisplayName(2, "Alice Zhang", NOW_LOCAL);
  }

  private OwnedMailAccount activeAccount(String displayName) {
    return new OwnedMailAccount(2, "stalwart-2", displayName, MailAccountStatus.ACTIVE);
  }

  private void assertBusinessError(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
  }
}
