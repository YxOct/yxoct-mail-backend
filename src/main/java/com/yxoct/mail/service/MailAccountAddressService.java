package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.mail.MailAccountEmailAddress;
import com.yxoct.mail.persistence.EmailAddressRepository;
import com.yxoct.mail.persistence.MailAccountSettingsRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MailAccountAddressService {

  private final MailAccountSettingsRepository accountRepository;
  private final EmailAddressRepository addressRepository;

  public MailAccountAddressService(
      MailAccountSettingsRepository accountRepository, EmailAddressRepository addressRepository) {
    this.accountRepository = accountRepository;
    this.addressRepository = addressRepository;
  }

  public List<MailAccountEmailAddress> list(String authenticatedUserId, long mailAccountId) {
    long userId = parseUserId(authenticatedUserId);
    accountRepository
        .findOwned(userId, mailAccountId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    return addressRepository.findAllByMailAccountId(mailAccountId).stream()
        .map(
            address ->
                new MailAccountEmailAddress(
                    address.getId(), address.getAddress(), address.getAddressType()))
        .toList();
  }

  private long parseUserId(String subject) {
    try {
      return Long.parseLong(subject);
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED, exception);
    }
  }
}
