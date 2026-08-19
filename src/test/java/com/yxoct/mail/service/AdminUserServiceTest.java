package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.AdminUserSummary;
import com.yxoct.mail.persistence.AdminUserRecord;
import com.yxoct.mail.persistence.AdminUserRepository;
import com.yxoct.mail.persistence.UserStatusMailAccount;
import com.yxoct.mail.persistence.UserStatusManagementRepository;
import com.yxoct.mail.persistence.UserStatusTarget;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;
import com.yxoct.mail.persistence.entity.UserStatusAuditAction;
import com.yxoct.mail.persistence.entity.UserStatusAuditEntity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

  private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 19, 20, 0);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 19, 22, 0);

  @Mock private AdminUserRepository repository;
  @Mock private UserStatusManagementRepository statusRepository;
  @Mock private StalwartManagementClient managementClient;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private TransactionStatus transactionStatus;

  private AdminUserService service;

  @BeforeEach
  void setUp() {
    lenient()
        .doAnswer(
            invocation -> {
              Consumer<TransactionStatus> action = invocation.getArgument(0);
              action.accept(transactionStatus);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());
    Clock clock = Clock.fixed(Instant.parse("2026-08-19T14:00:00Z"), ZoneId.of("Asia/Shanghai"));
    service =
        new AdminUserService(
            repository, statusRepository, managementClient, transactionTemplate, clock);
  }

  @Test
  void returnsAUserPageWithoutSensitiveCredentials() {
    AdminUserRecord record = userRecord();
    when(repository.count()).thenReturn(1L);
    when(repository.findPage(2, 20)).thenReturn(List.of(record));

    assertThat(service.list(2, 20).items()).containsExactly(summary());
  }

  @Test
  void returnsAUserById() {
    when(repository.findById(7)).thenReturn(Optional.of(userRecord()));

    assertThat(service.get(7)).isEqualTo(summary());
  }

  @Test
  void rejectsAnUnknownUser() {
    when(repository.findById(7)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(7))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
  }

  @Test
  void disablesAUserLocallyAndRemotelyAndWritesAnAudit() {
    when(statusRepository.findUserForUpdate(7))
        .thenReturn(Optional.of(new UserStatusTarget(7, UserRole.USER, UserStatus.ACTIVE)));
    when(statusRepository.findOwnedMailAccountsForUpdate(7))
        .thenReturn(List.of(new UserStatusMailAccount(9, "stalwart-9", MailAccountStatus.ACTIVE)));
    when(statusRepository.disableUser(7, 42, "Policy violation", NOW)).thenReturn(true);

    service.disable(42, 7, "  Policy violation  ");

    verify(managementClient).setAccountEnabled("stalwart-9", false);
    verify(statusRepository).disableOwnedMailAccounts(7, NOW);
    verify(statusRepository).revokeRefreshTokens(7, NOW);
    ArgumentCaptor<UserStatusAuditEntity> audit =
        ArgumentCaptor.forClass(UserStatusAuditEntity.class);
    verify(statusRepository).saveAudit(audit.capture());
    assertThat(audit.getValue().getUserId()).isEqualTo(7);
    assertThat(audit.getValue().getAction()).isEqualTo(UserStatusAuditAction.DISABLED);
    assertThat(audit.getValue().getReason()).isEqualTo("Policy violation");
    assertThat(audit.getValue().getOperatedByUserId()).isEqualTo(42);
    assertThat(audit.getValue().getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  void treatsAnAlreadyDisabledUserAsAnIdempotentSuccess() {
    when(statusRepository.findUserForUpdate(7))
        .thenReturn(Optional.of(new UserStatusTarget(7, UserRole.USER, UserStatus.DISABLED)));

    service.disable(42, 7, "Policy violation");

    verify(statusRepository, never()).disableUser(anyLong(), anyLong(), any(), any());
    verify(managementClient, never()).setAccountEnabled(any(), any(Boolean.class));
  }

  @Test
  void rejectsDisablingTheCurrentAdministrator() {
    assertBusinessError(
        () -> service.disable(42, 42, "Policy violation"), ErrorCode.CANNOT_DISABLE_SELF);
  }

  @Test
  void rejectsDisablingTheLastActiveAdministrator() {
    when(statusRepository.findUserForUpdate(7))
        .thenReturn(Optional.of(new UserStatusTarget(7, UserRole.ADMIN, UserStatus.ACTIVE)));
    when(statusRepository.findActiveAdministratorIdsForUpdate()).thenReturn(List.of(7L));

    assertBusinessError(
        () -> service.disable(42, 7, "Policy violation"), ErrorCode.CANNOT_DISABLE_LAST_ADMIN);
  }

  @Test
  void mapsARejectedRemoteDisableToMailServiceUnavailable() {
    when(statusRepository.findUserForUpdate(7))
        .thenReturn(Optional.of(new UserStatusTarget(7, UserRole.USER, UserStatus.ACTIVE)));
    when(statusRepository.findOwnedMailAccountsForUpdate(7))
        .thenReturn(List.of(new UserStatusMailAccount(9, "stalwart-9", MailAccountStatus.ACTIVE)));
    doThrow(new StalwartProvisioningException("ACCOUNT_STATUS_UPDATE_REJECTED"))
        .when(managementClient)
        .setAccountEnabled("stalwart-9", false);

    assertBusinessError(
        () -> service.disable(42, 7, "Policy violation"), ErrorCode.MAIL_SERVICE_UNAVAILABLE);
    verify(statusRepository, never()).disableOwnedMailAccounts(anyLong(), any());
  }

  @Test
  void restoresTheRemoteAccountWhenLocalPersistenceFails() {
    when(statusRepository.findUserForUpdate(7))
        .thenReturn(Optional.of(new UserStatusTarget(7, UserRole.USER, UserStatus.ACTIVE)));
    when(statusRepository.findOwnedMailAccountsForUpdate(7))
        .thenReturn(List.of(new UserStatusMailAccount(9, "stalwart-9", MailAccountStatus.ACTIVE)));
    when(statusRepository.disableUser(7, 42, "Policy violation", NOW))
        .thenThrow(new IllegalStateException("database failure"));

    assertThatThrownBy(() -> service.disable(42, 7, "Policy violation"))
        .isInstanceOf(IllegalStateException.class);
    verify(managementClient).setAccountEnabled("stalwart-9", false);
    verify(managementClient).setAccountEnabled("stalwart-9", true);
  }

  private void assertBusinessError(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
  }

  private AdminUserRecord userRecord() {
    return new AdminUserRecord(
        7,
        "alice@yxoct.com",
        "Alice",
        UserRole.USER,
        UserStatus.ACTIVE,
        9L,
        MailAccountStatus.ACTIVE,
        CREATED_AT);
  }

  private AdminUserSummary summary() {
    return new AdminUserSummary(
        7,
        "alice@yxoct.com",
        "Alice",
        UserRole.USER,
        UserStatus.ACTIVE,
        9L,
        MailAccountStatus.ACTIVE,
        CREATED_AT);
  }
}
