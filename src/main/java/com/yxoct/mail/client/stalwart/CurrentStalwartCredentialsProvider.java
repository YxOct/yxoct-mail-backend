package com.yxoct.mail.client.stalwart;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.config.StalwartProperties;
import com.yxoct.mail.persistence.MailAccountCredential;
import com.yxoct.mail.persistence.MailAccountCredentialRepository;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.service.MailCredentialCipher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CurrentStalwartCredentialsProvider implements StalwartCredentialsProvider {

  private static final String REQUEST_ATTRIBUTE =
      CurrentStalwartCredentialsProvider.class.getName() + ".credentials";

  private final StalwartProperties properties;
  private final MailAccountCredentialRepository repository;
  private final MailCredentialCipher credentialCipher;

  public CurrentStalwartCredentialsProvider(
      StalwartProperties properties,
      MailAccountCredentialRepository repository,
      MailCredentialCipher credentialCipher) {
    this.properties = properties;
    this.repository = repository;
    this.credentialCipher = credentialCipher;
  }

  @Override
  public StalwartCredentials getCredentials() {
    HttpServletRequest request = currentRequest();
    if (request != null) {
      Object cached = request.getAttribute(REQUEST_ATTRIBUTE);
      if (cached instanceof StalwartCredentials credentials) {
        return credentials;
      }
    }

    StalwartCredentials credentials = resolveCredentials();
    if (request != null) {
      request.setAttribute(REQUEST_ATTRIBUTE, credentials);
    }
    return credentials;
  }

  private StalwartCredentials resolveCredentials() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      return new StalwartCredentials(
          "configured:" + properties.username(), properties.username(), properties.password());
    }

    long userId;
    try {
      userId = Long.parseLong(jwt.getSubject());
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED, exception);
    }

    MailAccountCredential account =
        repository
            .findOwnedPrimaryAccount(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MAIL_ACCOUNT_NOT_READY));
    if (account.status() != MailAccountStatus.ACTIVE
        || account.emailAddress() == null
        || account.emailAddress().isBlank()
        || account.credentialCiphertext() == null
        || account.credentialCiphertext().isBlank()) {
      throw new BusinessException(ErrorCode.MAIL_ACCOUNT_NOT_READY);
    }

    try {
      return new StalwartCredentials(
          "user:" + userId + ":account:" + account.mailAccountId(),
          account.emailAddress(),
          credentialCipher.decrypt(account.credentialCiphertext()));
    } catch (IllegalStateException exception) {
      throw new BusinessException(ErrorCode.MAIL_SERVICE_AUTHENTICATION_FAILED, exception);
    }
  }

  private HttpServletRequest currentRequest() {
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes) {
      return attributes.getRequest();
    }
    return null;
  }
}
