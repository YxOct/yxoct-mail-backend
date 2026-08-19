package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.CurrentUserResponse;
import com.yxoct.mail.persistence.CurrentUserAccount;
import com.yxoct.mail.persistence.CurrentUserRepository;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

  @Mock private CurrentUserRepository repository;

  @Test
  void returnsTheOwnedPrimaryMailAccount() {
    CurrentUserAccount account =
        new CurrentUserAccount(
            1L,
            2L,
            "alice@yxoct.com",
            "Alice Zhang",
            UserRole.USER,
            UserStatus.ACTIVE,
            MailAccountStatus.ACTIVE);
    when(repository.findOwnedPrimaryAccount(1L)).thenReturn(Optional.of(account));

    CurrentUserResponse response = new CurrentUserService(repository).get("1");

    assertThat(response)
        .isEqualTo(
            new CurrentUserResponse(
                1L,
                2L,
                "alice@yxoct.com",
                "Alice Zhang",
                UserRole.USER,
                UserStatus.ACTIVE,
                MailAccountStatus.ACTIVE));
  }

  @Test
  void rejectsAnInvalidJwtSubject() {
    CurrentUserService service = new CurrentUserService(repository);

    assertAuthenticationFailure(() -> service.get("not-a-user-id"));
  }

  @Test
  void rejectsAUserWithoutAnOwnedPrimaryMailAccount() {
    when(repository.findOwnedPrimaryAccount(1L)).thenReturn(Optional.empty());
    CurrentUserService service = new CurrentUserService(repository);

    assertAuthenticationFailure(() -> service.get("1"));
  }

  private void assertAuthenticationFailure(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_FAILED));
  }
}
