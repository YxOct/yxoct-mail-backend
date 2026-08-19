package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.config.RegistrationProperties;
import com.yxoct.mail.domain.mail.EmailAliasResult;
import com.yxoct.mail.persistence.EmailAddressRepository;
import com.yxoct.mail.persistence.MailAccountSettingsRepository;
import com.yxoct.mail.persistence.OwnedMailAccount;
import com.yxoct.mail.persistence.RegistrationInvitationRepository;
import com.yxoct.mail.persistence.entity.EmailAddressEntity;
import com.yxoct.mail.persistence.entity.EmailAddressType;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.persistence.entity.RegistrationInvitationEntity;
import com.yxoct.mail.persistence.entity.RegistrationInvitationPurpose;
import com.yxoct.mail.persistence.entity.RegistrationInvitationStatus;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class EmailAliasServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-19T10:00:00Z");
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZONE);

  @Mock private PlatformTransactionManager transactionManager;
  @Mock private RegistrationInvitationRepository invitationRepository;
  @Mock private MailAccountSettingsRepository accountRepository;
  @Mock private EmailAddressRepository addressRepository;
  @Mock private StalwartManagementClient managementClient;

  private EmailAliasService service;
  private InvitationTokenCodec tokenCodec;

  @BeforeEach
  void setUp() {
    lenient()
        .when(
            transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class)))
        .thenReturn(new SimpleTransactionStatus());
    tokenCodec = new InvitationTokenCodec(new SecureRandom());
    service =
        new EmailAliasService(
            new TransactionTemplate(transactionManager),
            invitationRepository,
            new RegistrationInvitationValidator(),
            tokenCodec,
            new EmailAddressNormalizer(
                new RegistrationProperties("yxoct.com", Duration.ofHours(24), Set.of("billing"))),
            accountRepository,
            addressRepository,
            managementClient,
            Clock.fixed(NOW, ZONE));
  }

  @Test
  void consumesAnEmailAddressInvitationAndAddsTheAlias() {
    RegistrationInvitationEntity invitation =
        invitation(RegistrationInvitationPurpose.EMAIL_ADDRESS);
    when(invitationRepository.findByTokenHashForUpdate(tokenCodec.hash("yxi-token")))
        .thenReturn(Optional.of(invitation));
    when(accountRepository.findOwnedForUpdate(1, 2)).thenReturn(Optional.of(activeAccount()));
    when(managementClient.addAccountAlias("stalwart-2", "hello@yxoct.com")).thenReturn(true);
    when(invitationRepository.markUsed(7, 1, NOW_LOCAL)).thenReturn(true);

    assertThat(service.create("1", 2, "yxi-token", "Hello"))
        .isEqualTo(new EmailAliasResult(2, "hello@yxoct.com", EmailAddressType.ALIAS));

    verify(managementClient).addAccountAlias("stalwart-2", "hello@yxoct.com");
    verify(addressRepository).insertAlias(2, "hello@yxoct.com", NOW_LOCAL);
    verify(invitationRepository).markUsed(7, 1, NOW_LOCAL);
  }

  @Test
  void removesAnOwnedAliasRemotelyAndLocally() {
    when(accountRepository.findOwnedForUpdate(1, 2)).thenReturn(Optional.of(activeAccount()));
    when(addressRepository.findByIdForUpdate(2, 11))
        .thenReturn(Optional.of(address(11, EmailAddressType.ALIAS)));
    when(managementClient.removeAccountAlias("stalwart-2", "hello@yxoct.com")).thenReturn(true);
    when(addressRepository.deleteAlias(2, 11)).thenReturn(true);

    service.delete("1", 2, 11);

    verify(managementClient).removeAccountAlias("stalwart-2", "hello@yxoct.com");
    verify(addressRepository).deleteAlias(2, 11);
  }

  @Test
  void rejectsDeletingThePrimaryAddress() {
    when(accountRepository.findOwnedForUpdate(1, 2)).thenReturn(Optional.of(activeAccount()));
    when(addressRepository.findByIdForUpdate(2, 10))
        .thenReturn(Optional.of(address(10, EmailAddressType.PRIMARY)));

    assertThatThrownBy(() -> service.delete("1", 2, 10))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.PRIMARY_EMAIL_ADDRESS_CANNOT_BE_DELETED));

    verifyNoInteractions(managementClient);
    verify(addressRepository, never()).deleteAlias(2, 10);
  }

  @Test
  void removesTheLocalAliasWhenItIsAlreadyMissingRemotely() {
    when(accountRepository.findOwnedForUpdate(1, 2)).thenReturn(Optional.of(activeAccount()));
    when(addressRepository.findByIdForUpdate(2, 11))
        .thenReturn(Optional.of(address(11, EmailAddressType.ALIAS)));
    when(managementClient.removeAccountAlias("stalwart-2", "hello@yxoct.com")).thenReturn(false);
    when(addressRepository.deleteAlias(2, 11)).thenReturn(true);

    service.delete("1", 2, 11);

    verify(addressRepository).deleteAlias(2, 11);
    verify(managementClient, never()).addAccountAlias("stalwart-2", "hello@yxoct.com");
  }

  @Test
  void restoresTheRemoteAliasWhenTheLocalDeleteFails() {
    when(accountRepository.findOwnedForUpdate(1, 2)).thenReturn(Optional.of(activeAccount()));
    when(addressRepository.findByIdForUpdate(2, 11))
        .thenReturn(Optional.of(address(11, EmailAddressType.ALIAS)));
    when(managementClient.removeAccountAlias("stalwart-2", "hello@yxoct.com")).thenReturn(true);
    when(addressRepository.deleteAlias(2, 11)).thenReturn(false);

    assertThatThrownBy(() -> service.delete("1", 2, 11)).isInstanceOf(IllegalStateException.class);

    verify(managementClient).addAccountAlias("stalwart-2", "hello@yxoct.com");
  }

  @Test
  void keepsTheLocalAliasWhenStalwartRejectsTheRemoval() {
    when(accountRepository.findOwnedForUpdate(1, 2)).thenReturn(Optional.of(activeAccount()));
    when(addressRepository.findByIdForUpdate(2, 11))
        .thenReturn(Optional.of(address(11, EmailAddressType.ALIAS)));
    doThrow(new StalwartProvisioningException("ACCOUNT_ALIAS_UPDATE_REJECTED"))
        .when(managementClient)
        .removeAccountAlias("stalwart-2", "hello@yxoct.com");

    assertThatThrownBy(() -> service.delete("1", 2, 11))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MAIL_SERVICE_UNAVAILABLE));

    verify(addressRepository, never()).deleteAlias(2, 11);
  }

  @Test
  void rejectsCoreAndConfiguredReservedAliasesBeforeUsingTheInvitation() {
    assertBusinessError("OWNER", ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE);
    assertBusinessError("billing", ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE);

    verifyNoInteractions(
        transactionManager,
        invitationRepository,
        accountRepository,
        addressRepository,
        managementClient);
  }

  @Test
  void rejectsARegistrationInvitation() {
    when(invitationRepository.findByTokenHashForUpdate(tokenCodec.hash("yxi-token")))
        .thenReturn(Optional.of(invitation(RegistrationInvitationPurpose.REGISTRATION)));

    assertBusinessError(ErrorCode.INVITATION_INVALID);
    verify(managementClient, never()).addAccountAlias("stalwart-2", "hello@yxoct.com");
  }

  @Test
  void hidesAnAccountOwnedByAnotherUser() {
    when(invitationRepository.findByTokenHashForUpdate(tokenCodec.hash("yxi-token")))
        .thenReturn(Optional.of(invitation(RegistrationInvitationPurpose.EMAIL_ADDRESS)));
    when(accountRepository.findOwnedForUpdate(1, 2)).thenReturn(Optional.empty());

    assertBusinessError(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void doesNotConsumeTheInvitationWhenStalwartRejectsTheAlias() {
    when(invitationRepository.findByTokenHashForUpdate(tokenCodec.hash("yxi-token")))
        .thenReturn(Optional.of(invitation(RegistrationInvitationPurpose.EMAIL_ADDRESS)));
    when(accountRepository.findOwnedForUpdate(1, 2)).thenReturn(Optional.of(activeAccount()));
    doThrow(new StalwartProvisioningException("ACCOUNT_ALIAS_UPDATE_REJECTED"))
        .when(managementClient)
        .addAccountAlias("stalwart-2", "hello@yxoct.com");

    assertBusinessError(ErrorCode.MAIL_SERVICE_UNAVAILABLE);
    verify(addressRepository, never()).insertAlias(2, "hello@yxoct.com", NOW_LOCAL);
    verify(invitationRepository, never()).markUsed(7, 1, NOW_LOCAL);
  }

  @Test
  void removesANewRemoteAliasWhenTheLocalInsertFails() {
    when(invitationRepository.findByTokenHashForUpdate(tokenCodec.hash("yxi-token")))
        .thenReturn(Optional.of(invitation(RegistrationInvitationPurpose.EMAIL_ADDRESS)));
    when(accountRepository.findOwnedForUpdate(1, 2)).thenReturn(Optional.of(activeAccount()));
    when(managementClient.addAccountAlias("stalwart-2", "hello@yxoct.com")).thenReturn(true);
    doThrow(new org.springframework.dao.DuplicateKeyException("duplicate"))
        .when(addressRepository)
        .insertAlias(2, "hello@yxoct.com", NOW_LOCAL);

    assertBusinessError(ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE);

    verify(managementClient).removeAccountAlias("stalwart-2", "hello@yxoct.com");
    verify(invitationRepository, never()).markUsed(7, 1, NOW_LOCAL);
  }

  private void assertBusinessError(ErrorCode errorCode) {
    assertBusinessError("hello", errorCode);
  }

  private void assertBusinessError(String localPart, ErrorCode errorCode) {
    assertThatThrownBy(() -> service.create("1", 2, "yxi-token", localPart))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
  }

  private RegistrationInvitationEntity invitation(RegistrationInvitationPurpose purpose) {
    RegistrationInvitationEntity invitation = new RegistrationInvitationEntity();
    invitation.setId(7L);
    invitation.setPurpose(purpose);
    invitation.setStatus(RegistrationInvitationStatus.PENDING);
    invitation.setExpiresAt(NOW_LOCAL.plusHours(1));
    return invitation;
  }

  private OwnedMailAccount activeAccount() {
    return new OwnedMailAccount(2, "stalwart-2", "Alice", MailAccountStatus.ACTIVE);
  }

  private EmailAddressEntity address(long id, EmailAddressType type) {
    EmailAddressEntity address = new EmailAddressEntity();
    address.setId(id);
    address.setMailAccountId(2L);
    address.setAddress("hello@yxoct.com");
    address.setNormalizedAddress("hello@yxoct.com");
    address.setAddressType(type);
    return address;
  }
}
