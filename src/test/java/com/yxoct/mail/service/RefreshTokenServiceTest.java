package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.config.AuthenticationProperties;
import com.yxoct.mail.domain.user.AccessTokenResponse;
import com.yxoct.mail.domain.user.TokenPairResponse;
import com.yxoct.mail.persistence.AuthenticatedUser;
import com.yxoct.mail.persistence.AuthenticationUserRepository;
import com.yxoct.mail.persistence.RefreshTokenSessionRepository;
import com.yxoct.mail.persistence.entity.RefreshTokenSessionEntity;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;
import com.yxoct.mail.security.JwtTokenService;
import com.yxoct.mail.security.UserAuthVersionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  private static final String OLD_TOKEN = "a".repeat(43);
  private static final String NEW_TOKEN = "b".repeat(43);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 19, 1, 0);

  @Mock private RefreshTokenSessionRepository sessionRepository;
  @Mock private AuthenticationUserRepository userRepository;
  @Mock private RefreshTokenCodec tokenCodec;
  @Mock private JwtTokenService jwtTokenService;
  @Mock private UserAuthVersionStore authVersionStore;

  private RefreshTokenService service;

  @BeforeEach
  void setUp() {
    Clock clock =
        Clock.fixed(NOW.atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));
    AuthenticationProperties properties =
        new AuthenticationProperties(
            "issuer", "A".repeat(43), Duration.ofMinutes(15), Duration.ofDays(30));
    service =
        new RefreshTokenService(
            sessionRepository,
            userRepository,
            tokenCodec,
            jwtTokenService,
            authVersionStore,
            properties,
            clock);
  }

  @Test
  void rotatesActiveRefreshToken() {
    AuthenticatedUser user = activeUser();
    RefreshTokenSessionEntity oldSession = session(10L, NOW.plusDays(1), null);
    when(tokenCodec.hash(OLD_TOKEN)).thenReturn("old-hash");
    when(sessionRepository.findByTokenHashForUpdate("old-hash"))
        .thenReturn(Optional.of(oldSession));
    when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
    when(sessionRepository.revoke(10L, NOW)).thenReturn(true);
    when(tokenCodec.generate()).thenReturn(NEW_TOKEN);
    when(tokenCodec.hash(NEW_TOKEN)).thenReturn("new-hash");
    when(jwtTokenService.issue(user)).thenReturn(new AccessTokenResponse("access", "Bearer", 900));

    TokenPairResponse result = service.refresh(OLD_TOKEN);

    assertThat(result.refreshToken()).isEqualTo(NEW_TOKEN);
    assertThat(result.refreshExpiresIn()).isEqualTo(Duration.ofDays(30).toSeconds());
    verify(authVersionStore).setVersionIfGreater(1L, 3L);
    ArgumentCaptor<RefreshTokenSessionEntity> saved =
        ArgumentCaptor.forClass(RefreshTokenSessionEntity.class);
    verify(sessionRepository).save(saved.capture());
    assertThat(saved.getValue().getTokenHash()).isEqualTo("new-hash");
    assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusDays(30));
  }

  @Test
  void rejectsExpiredRefreshToken() {
    when(tokenCodec.hash(OLD_TOKEN)).thenReturn("old-hash");
    when(sessionRepository.findByTokenHashForUpdate("old-hash"))
        .thenReturn(Optional.of(session(10L, NOW, null)));

    assertInvalid(() -> service.refresh(OLD_TOKEN));
  }

  @Test
  void logoutIsIdempotent() {
    when(tokenCodec.hash(OLD_TOKEN)).thenReturn("old-hash");

    service.revoke(OLD_TOKEN);

    verify(sessionRepository).revokeByTokenHash("old-hash", NOW);
  }

  private RefreshTokenSessionEntity session(
      long id, LocalDateTime expiresAt, LocalDateTime revokedAt) {
    RefreshTokenSessionEntity session = new RefreshTokenSessionEntity();
    session.setId(id);
    session.setUserId(1L);
    session.setExpiresAt(expiresAt);
    session.setRevokedAt(revokedAt);
    return session;
  }

  private AuthenticatedUser activeUser() {
    return new AuthenticatedUser(
        1L, "alice@yxoct.com", "hash", UserStatus.ACTIVE, UserRole.USER, 3L);
  }

  private void assertInvalid(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID));
  }
}
