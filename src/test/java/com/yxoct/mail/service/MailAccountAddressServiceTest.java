package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.mail.MailAccountEmailAddress;
import com.yxoct.mail.persistence.EmailAddressRepository;
import com.yxoct.mail.persistence.MailAccountSettingsRepository;
import com.yxoct.mail.persistence.OwnedMailAccount;
import com.yxoct.mail.persistence.entity.EmailAddressEntity;
import com.yxoct.mail.persistence.entity.EmailAddressType;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailAccountAddressServiceTest {

  @Mock private MailAccountSettingsRepository accountRepository;
  @Mock private EmailAddressRepository addressRepository;

  private MailAccountAddressService service;

  @BeforeEach
  void setUp() {
    service = new MailAccountAddressService(accountRepository, addressRepository);
  }

  @Test
  void listsPrimaryAddressAndAliasesForAnOwnedAccount() {
    when(accountRepository.findOwned(1, 2))
        .thenReturn(
            Optional.of(new OwnedMailAccount(2, "stalwart-2", "Alice", MailAccountStatus.ACTIVE)));
    when(addressRepository.findAllByMailAccountId(2))
        .thenReturn(
            List.of(
                address(10, "alice@yxoct.com", EmailAddressType.PRIMARY),
                address(11, "hello@yxoct.com", EmailAddressType.ALIAS)));

    assertThat(service.list("1", 2))
        .containsExactly(
            new MailAccountEmailAddress(10, "alice@yxoct.com", EmailAddressType.PRIMARY),
            new MailAccountEmailAddress(11, "hello@yxoct.com", EmailAddressType.ALIAS));
  }

  @Test
  void hidesAnAccountOwnedByAnotherUser() {
    when(accountRepository.findOwned(1, 2)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.list("1", 2))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

    verifyNoInteractions(addressRepository);
  }

  @Test
  void rejectsAnInvalidAuthenticatedSubject() {
    assertThatThrownBy(() -> service.list("invalid", 2))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_FAILED));

    verifyNoInteractions(accountRepository, addressRepository);
  }

  private EmailAddressEntity address(long id, String value, EmailAddressType type) {
    EmailAddressEntity address = new EmailAddressEntity();
    address.setId(id);
    address.setMailAccountId(2L);
    address.setAddress(value);
    address.setNormalizedAddress(value);
    address.setAddressType(type);
    return address;
  }
}
