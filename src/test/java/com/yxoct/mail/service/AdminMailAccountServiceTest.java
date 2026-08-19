package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.persistence.AdminMailAccountProvisioningRecord;
import com.yxoct.mail.persistence.AdminMailAccountProvisioningTarget;
import com.yxoct.mail.persistence.AdminMailAccountRepository;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminMailAccountServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final Instant INSTANT = Instant.parse("2026-08-19T12:00:00Z");
  private static final LocalDateTime NOW = LocalDateTime.ofInstant(INSTANT, ZONE);

  @Mock private AdminMailAccountRepository repository;
  private AdminMailAccountService service;

  @BeforeEach
  void setUp() {
    service = new AdminMailAccountService(repository, Clock.fixed(INSTANT, ZONE));
  }

  @Test
  void listsProvisioningIssues() {
    when(repository.countProvisioningIssues()).thenReturn(1L);
    when(repository.findProvisioningIssues(2, 25))
        .thenReturn(
            List.of(
                new AdminMailAccountProvisioningRecord(
                    9,
                    7,
                    "alice@yxoct.com",
                    MailAccountStatus.FAILED,
                    3,
                    "MANAGEMENT_REQUEST_FAILED",
                    NOW,
                    null,
                    NOW)));

    var page = service.listProvisioningIssues(2, 25);

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().getFirst().emailAddress()).isEqualTo("alice@yxoct.com");
  }

  @Test
  void schedulesFailedAccountForImmediateRetryAndAuditsIt() {
    when(repository.findForRetryForUpdate(9))
        .thenReturn(
            Optional.of(
                new AdminMailAccountProvisioningTarget(9, 7, MailAccountStatus.FAILED, null)));
    when(repository.scheduleRetry(9, NOW)).thenReturn(true);

    service.retryProvisioning(42, 9);

    verify(repository).scheduleRetry(9, NOW);
    verify(repository).saveRetryAudit(7, 42, 9, NOW);
  }

  @Test
  void schedulesProvisioningAccountWhenItsLeaseExpired() {
    when(repository.findForRetryForUpdate(9))
        .thenReturn(
            Optional.of(
                new AdminMailAccountProvisioningTarget(
                    9, 7, MailAccountStatus.PROVISIONING, NOW.minusSeconds(1))));
    when(repository.scheduleRetry(9, NOW)).thenReturn(true);

    service.retryProvisioning(42, 9);

    verify(repository).scheduleRetry(9, NOW);
  }

  @Test
  void rejectsAccountWithAnActiveProvisioningLease() {
    when(repository.findForRetryForUpdate(9))
        .thenReturn(
            Optional.of(
                new AdminMailAccountProvisioningTarget(
                    9, 7, MailAccountStatus.PROVISIONING, NOW.plusMinutes(1))));

    assertThatThrownBy(() -> service.retryProvisioning(42, 9))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.MAIL_ACCOUNT_RETRY_CONFLICT));
    verify(repository, never()).scheduleRetry(9, NOW);
  }

  @Test
  void rejectsActiveAccount() {
    when(repository.findForRetryForUpdate(9))
        .thenReturn(
            Optional.of(
                new AdminMailAccountProvisioningTarget(9, 7, MailAccountStatus.ACTIVE, null)));

    assertThatThrownBy(() -> service.retryProvisioning(42, 9))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.MAIL_ACCOUNT_RETRY_CONFLICT));
  }

  @Test
  void rejectsUnknownAccount() {
    when(repository.findForRetryForUpdate(9)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.retryProvisioning(42, 9))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
  }
}
