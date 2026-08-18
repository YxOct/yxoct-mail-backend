package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.AccessTokenResponse;
import com.yxoct.mail.domain.user.LoginRequest;
import com.yxoct.mail.persistence.AuthenticatedUser;
import com.yxoct.mail.persistence.AuthenticationUserRepository;
import com.yxoct.mail.persistence.entity.UserStatus;
import com.yxoct.mail.security.JwtTokenService;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

  private final AuthenticationUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService jwtTokenService;

  public LoginService(
      AuthenticationUserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenService jwtTokenService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenService = jwtTokenService;
  }

  public AccessTokenResponse login(LoginRequest request) {
    String normalizedAddress = request.emailAddress().trim().toLowerCase(Locale.ROOT);
    AuthenticatedUser user =
        userRepository
            .findByEmailAddress(normalizedAddress)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_FAILED));
    if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
      throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
    }
    if (user.status() != UserStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
    }
    return jwtTokenService.issue(user);
  }
}
