package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.LoginRequest;
import com.yxoct.mail.domain.user.TokenPairResponse;
import com.yxoct.mail.persistence.AuthenticatedUser;
import com.yxoct.mail.persistence.AuthenticationUserRepository;
import com.yxoct.mail.persistence.entity.UserStatus;
import com.yxoct.mail.security.LoginRateLimiter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

  private final AuthenticationUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final RefreshTokenService refreshTokenService;
  private final LoginRateLimiter rateLimiter;

  @Autowired
  public LoginService(
      AuthenticationUserRepository userRepository,
      PasswordEncoder passwordEncoder,
      RefreshTokenService refreshTokenService,
      LoginRateLimiter rateLimiter) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.refreshTokenService = refreshTokenService;
    this.rateLimiter = rateLimiter;
  }

  public LoginService(
      AuthenticationUserRepository userRepository,
      PasswordEncoder passwordEncoder,
      RefreshTokenService refreshTokenService) {
    this(userRepository, passwordEncoder, refreshTokenService, null);
  }

  public TokenPairResponse login(LoginRequest request, String ipAddress) {
    String normalizedAddress = request.emailAddress().trim().toLowerCase(Locale.ROOT);
    if (rateLimiter != null) {
      rateLimiter.check(normalizedAddress, ipAddress);
    }
    AuthenticatedUser user =
        userRepository
            .findByEmailAddress(normalizedAddress)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS));
    if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
      throw new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS);
    }
    if (user.status() != UserStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
    }
    if (rateLimiter != null) {
      rateLimiter.clear(normalizedAddress, ipAddress);
    }
    return refreshTokenService.issueFor(user);
  }

  public TokenPairResponse login(LoginRequest request) {
    return login(request, "unknown");
  }
}
