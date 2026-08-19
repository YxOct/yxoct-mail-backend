package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.CurrentUserResponse;
import com.yxoct.mail.persistence.CurrentUserAccount;
import com.yxoct.mail.persistence.CurrentUserRepository;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

  private final CurrentUserRepository repository;

  public CurrentUserService(CurrentUserRepository repository) {
    this.repository = repository;
  }

  public CurrentUserResponse get(String subject) {
    long userId;
    try {
      userId = Long.parseLong(subject);
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED, exception);
    }
    CurrentUserAccount account =
        repository
            .findOwnedPrimaryAccount(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_FAILED));
    return new CurrentUserResponse(
        account.userId(),
        account.mailAccountId(),
        account.emailAddress(),
        account.displayName(),
        account.role(),
        account.status(),
        account.mailAccountStatus(),
        account.mustChangePassword());
  }
}
