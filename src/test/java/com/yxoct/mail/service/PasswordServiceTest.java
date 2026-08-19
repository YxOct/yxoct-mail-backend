package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.persistence.PasswordChangeTarget;
import com.yxoct.mail.persistence.UserPasswordRepository;
import com.yxoct.mail.persistence.entity.UserStatusAuditAction;
import com.yxoct.mail.persistence.entity.UserStatusAuditEntity;
import com.yxoct.mail.security.UserAuthVersionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 0, 0);
  private static final String CURRENT_PASSWORD = "correct horse battery staple";
  private static final String NEW_PASSWORD = "new correct horse battery staple";

  @Mock private UserPasswordRepository repository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private UserAuthVersionStore authVersionStore;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private TransactionStatus transactionStatus;

  private PasswordService service;

  @BeforeEach
  void setUp() {
    doAnswer(
            invocation -> {
              Consumer<TransactionStatus> action = invocation.getArgument(0);
              action.accept(transactionStatus);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());
    Clock clock = Clock.fixed(Instant.parse("2026-08-19T16:00:00Z"), ZoneId.of("Asia/Shanghai"));
    service =
        new PasswordService(
            repository, passwordEncoder, authVersionStore, transactionTemplate, clock);
  }

  @Test
  void changesPasswordRevokesSessionsAndWritesAudit() {
    when(repository.findForUpdate(7))
        .thenReturn(Optional.of(new PasswordChangeTarget(7, "old-hash", 3)));
    when(passwordEncoder.matches(CURRENT_PASSWORD, "old-hash")).thenReturn(true);
    when(passwordEncoder.matches(NEW_PASSWORD, "old-hash")).thenReturn(false);
    when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("new-hash");
    when(repository.updatePassword(7, 3, "new-hash", NOW)).thenReturn(true);

    service.change(7, CURRENT_PASSWORD, NEW_PASSWORD);

    verify(authVersionStore).setVersion(7, 4);
    verify(repository).revokeRefreshTokens(7, NOW);
    ArgumentCaptor<UserStatusAuditEntity> audit =
        ArgumentCaptor.forClass(UserStatusAuditEntity.class);
    verify(repository).saveAudit(audit.capture());
    assertThat(audit.getValue().getAction()).isEqualTo(UserStatusAuditAction.PASSWORD_CHANGED);
    assertThat(audit.getValue().getOperatedByUserId()).isEqualTo(7);
    assertThat(audit.getValue().getReason()).isNull();
  }

  @Test
  void rejectsAnInvalidCurrentPassword() {
    when(repository.findForUpdate(7))
        .thenReturn(Optional.of(new PasswordChangeTarget(7, "old-hash", 3)));
    when(passwordEncoder.matches(CURRENT_PASSWORD, "old-hash")).thenReturn(false);

    assertBusinessError(
        () -> service.change(7, CURRENT_PASSWORD, NEW_PASSWORD),
        ErrorCode.CURRENT_PASSWORD_INVALID);

    verify(repository, never()).updatePassword(anyLong(), anyLong(), any(), any());
  }

  @Test
  void rejectsReusingTheCurrentPassword() {
    when(repository.findForUpdate(7))
        .thenReturn(Optional.of(new PasswordChangeTarget(7, "old-hash", 3)));
    when(passwordEncoder.matches(CURRENT_PASSWORD, "old-hash")).thenReturn(true);
    when(passwordEncoder.matches(NEW_PASSWORD, "old-hash")).thenReturn(true);

    assertBusinessError(
        () -> service.change(7, CURRENT_PASSWORD, NEW_PASSWORD),
        ErrorCode.NEW_PASSWORD_MUST_DIFFER);
  }

  @Test
  void restoresAuthenticationVersionWhenPersistenceFails() {
    when(repository.findForUpdate(7))
        .thenReturn(Optional.of(new PasswordChangeTarget(7, "old-hash", 3)));
    when(passwordEncoder.matches(CURRENT_PASSWORD, "old-hash")).thenReturn(true);
    when(passwordEncoder.matches(NEW_PASSWORD, "old-hash")).thenReturn(false);
    when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("new-hash");
    when(repository.updatePassword(7, 3, "new-hash", NOW)).thenReturn(false);

    assertThatThrownBy(() -> service.change(7, CURRENT_PASSWORD, NEW_PASSWORD))
        .isInstanceOf(IllegalStateException.class);

    verify(authVersionStore).setVersion(7, 4);
    verify(authVersionStore).setVersion(7, 3);
  }

  private void assertBusinessError(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
  }
}
