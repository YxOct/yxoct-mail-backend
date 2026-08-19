package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.config.AuthenticationProperties;
import com.yxoct.mail.domain.user.AccessTokenResponse;
import com.yxoct.mail.domain.user.TokenPairResponse;
import com.yxoct.mail.persistence.AuthenticatedUser;
import com.yxoct.mail.persistence.AuthenticationUserRepository;
import com.yxoct.mail.persistence.RefreshTokenSessionRepository;
import com.yxoct.mail.persistence.entity.RefreshTokenSessionEntity;
import com.yxoct.mail.persistence.entity.UserStatus;
import com.yxoct.mail.security.JwtTokenService;
import com.yxoct.mail.security.UserAuthVersionStore;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

  private final RefreshTokenSessionRepository sessionRepository;
  private final AuthenticationUserRepository userRepository;
  private final RefreshTokenCodec tokenCodec;
  private final JwtTokenService jwtTokenService;
  private final UserAuthVersionStore authVersionStore;
  private final AuthenticationProperties properties;
  private final Clock clock;

  public RefreshTokenService(
      RefreshTokenSessionRepository sessionRepository,
      AuthenticationUserRepository userRepository,
      RefreshTokenCodec tokenCodec,
      JwtTokenService jwtTokenService,
      UserAuthVersionStore authVersionStore,
      AuthenticationProperties properties,
      Clock clock) {
    this.sessionRepository = sessionRepository;
    this.userRepository = userRepository;
    this.tokenCodec = tokenCodec;
    this.jwtTokenService = jwtTokenService;
    this.authVersionStore = authVersionStore;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public TokenPairResponse issueFor(AuthenticatedUser user) {
    return createTokenPair(user, now());
  }

  @Transactional
  public TokenPairResponse refresh(String refreshToken) {
    LocalDateTime now = now();
    RefreshTokenSessionEntity session =
        sessionRepository
            .findByTokenHashForUpdate(tokenCodec.hash(refreshToken))
            .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
    if (session.getRevokedAt() != null || !now.isBefore(session.getExpiresAt())) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
    }
    AuthenticatedUser user =
        userRepository
            .findByUserId(session.getUserId())
            .filter(candidate -> candidate.status() == UserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
    if (!sessionRepository.revoke(session.getId(), now)) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
    }
    return createTokenPair(user, now);
  }

  @Transactional
  public void revoke(String refreshToken) {
    sessionRepository.revokeByTokenHash(tokenCodec.hash(refreshToken), now());
  }

  private TokenPairResponse createTokenPair(AuthenticatedUser user, LocalDateTime now) {
    authVersionStore.setVersionIfGreater(user.userId(), user.version());
    String refreshToken = tokenCodec.generate();
    RefreshTokenSessionEntity session = new RefreshTokenSessionEntity();
    session.setUserId(user.userId());
    session.setTokenHash(tokenCodec.hash(refreshToken));
    session.setExpiresAt(now.plus(properties.refreshTokenTtl()));
    sessionRepository.save(session);

    AccessTokenResponse access = jwtTokenService.issue(user);
    return new TokenPairResponse(
        access.accessToken(),
        access.tokenType(),
        access.expiresIn(),
        refreshToken,
        properties.refreshTokenTtl().toSeconds());
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
  }
}
