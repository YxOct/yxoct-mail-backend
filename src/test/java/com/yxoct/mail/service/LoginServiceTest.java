package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.LoginRequest;
import com.yxoct.mail.domain.user.TokenPairResponse;
import com.yxoct.mail.persistence.AuthenticatedUser;
import com.yxoct.mail.persistence.AuthenticationUserRepository;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

  private static final String PASSWORD = "correct horse battery staple";
  private static final String PASSWORD_HASH = "{argon2}hash";

  @Mock private AuthenticationUserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private RefreshTokenService refreshTokenService;

  private LoginService loginService;

  @BeforeEach
  void setUp() {
    loginService = new LoginService(userRepository, passwordEncoder, refreshTokenService);
  }

  @Test
  void normalizesAddressAndIssuesAccessToken() {
    AuthenticatedUser user = activeUser();
    TokenPairResponse expected =
        new TokenPairResponse("token", "Bearer", 900, "a".repeat(43), 2_592_000);
    when(userRepository.findByEmailAddress("alice@yxoct.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
    when(refreshTokenService.issueFor(user)).thenReturn(expected);

    TokenPairResponse result = loginService.login(new LoginRequest(" Alice@YXOct.com ", PASSWORD));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void usesSameFailureForUnknownAddressAndWrongPassword() {
    when(userRepository.findByEmailAddress("missing@yxoct.com")).thenReturn(Optional.empty());

    assertAuthenticationFailure(
        () -> loginService.login(new LoginRequest("missing@yxoct.com", PASSWORD)));

    AuthenticatedUser user = activeUser();
    when(userRepository.findByEmailAddress("alice@yxoct.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);

    assertAuthenticationFailure(
        () -> loginService.login(new LoginRequest("alice@yxoct.com", PASSWORD)));
  }

  @Test
  void rejectsDisabledUserAfterPasswordVerification() {
    AuthenticatedUser user =
        new AuthenticatedUser(
            1L, "alice@yxoct.com", PASSWORD_HASH, UserStatus.DISABLED, UserRole.USER);
    when(userRepository.findByEmailAddress("alice@yxoct.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);

    assertThatThrownBy(() -> loginService.login(new LoginRequest("alice@yxoct.com", PASSWORD)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_DISABLED));
  }

  private AuthenticatedUser activeUser() {
    return new AuthenticatedUser(
        1L, "alice@yxoct.com", PASSWORD_HASH, UserStatus.ACTIVE, UserRole.USER);
  }

  private void assertAuthenticationFailure(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_FAILED));
  }
}
