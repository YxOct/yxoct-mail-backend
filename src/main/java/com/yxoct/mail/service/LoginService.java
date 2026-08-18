package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.LoginRequest;
import com.yxoct.mail.domain.user.TokenPairResponse;
import com.yxoct.mail.persistence.AuthenticatedUser;
import com.yxoct.mail.persistence.AuthenticationUserRepository;
import com.yxoct.mail.persistence.entity.UserStatus;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

  private final AuthenticationUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final RefreshTokenService refreshTokenService;

  public LoginService(
      AuthenticationUserRepository userRepository,
      PasswordEncoder passwordEncoder,
      RefreshTokenService refreshTokenService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.refreshTokenService = refreshTokenService;
  }

  public TokenPairResponse login(LoginRequest request) {
    String normalizedAddress = request.emailAddress().trim().toLowerCase(Locale.ROOT);
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
    return refreshTokenService.issueFor(user);
  }
}
