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
import com.yxoct.mail.persistence.EmailAliasRepository;
import com.yxoct.mail.persistence.MailAccountSettingsRepository;
import com.yxoct.mail.persistence.OwnedMailAccount;
import com.yxoct.mail.persistence.RegistrationInvitationRepository;
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
  @Mock private EmailAliasRepository aliasRepository;
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
            aliasRepository,
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
    verify(aliasRepository).insert(2, "hello@yxoct.com", NOW_LOCAL);
    verify(invitationRepository).markUsed(7, 1, NOW_LOCAL);
  }

  @Test
  void rejectsCoreAndConfiguredReservedAliasesBeforeUsingTheInvitation() {
    assertBusinessError("OWNER", ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE);
    assertBusinessError("billing", ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE);

    verifyNoInteractions(
        transactionManager,
        invitationRepository,
        accountRepository,
        aliasRepository,
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
    verify(aliasRepository, never()).insert(2, "hello@yxoct.com", NOW_LOCAL);
    verify(invitationRepository, never()).markUsed(7, 1, NOW_LOCAL);
  }

  @Test
  void removesANewRemoteAliasWhenTheLocalInsertFails() {
    when(invitationRepository.findByTokenHashForUpdate(tokenCodec.hash("yxi-token")))
        .thenReturn(Optional.of(invitation(RegistrationInvitationPurpose.EMAIL_ADDRESS)));
    when(accountRepository.findOwnedForUpdate(1, 2)).thenReturn(Optional.of(activeAccount()));
    when(managementClient.addAccountAlias("stalwart-2", "hello@yxoct.com")).thenReturn(true);
    doThrow(new org.springframework.dao.DuplicateKeyException("duplicate"))
        .when(aliasRepository)
        .insert(2, "hello@yxoct.com", NOW_LOCAL);

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
}
