package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.AdminUserSummary;
import com.yxoct.mail.persistence.AdminUserRecord;
import com.yxoct.mail.persistence.AdminUserRepository;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

  private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 19, 20, 0);

  @Mock private AdminUserRepository repository;

  @Test
  void returnsAUserPageWithoutSensitiveCredentials() {
    AdminUserRecord record = userRecord();
    when(repository.count()).thenReturn(1L);
    when(repository.findPage(2, 20)).thenReturn(List.of(record));

    assertThat(new AdminUserService(repository).list(2, 20).items()).containsExactly(summary());
  }

  @Test
  void returnsAUserById() {
    when(repository.findById(7)).thenReturn(Optional.of(userRecord()));

    assertThat(new AdminUserService(repository).get(7)).isEqualTo(summary());
  }

  @Test
  void rejectsAnUnknownUser() {
    when(repository.findById(7)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new AdminUserService(repository).get(7))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
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
