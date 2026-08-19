package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EmailAliasService {

  private static final Logger log = LoggerFactory.getLogger(EmailAliasService.class);

  private final TransactionTemplate transactionTemplate;
  private final RegistrationInvitationRepository invitationRepository;
  private final RegistrationInvitationValidator invitationValidator;
  private final InvitationTokenCodec tokenCodec;
  private final EmailAddressNormalizer addressNormalizer;
  private final MailAccountSettingsRepository accountRepository;
  private final EmailAddressRepository addressRepository;
  private final StalwartManagementClient managementClient;
  private final Clock clock;

  public EmailAliasService(
      TransactionTemplate transactionTemplate,
      RegistrationInvitationRepository invitationRepository,
      RegistrationInvitationValidator invitationValidator,
      InvitationTokenCodec tokenCodec,
      EmailAddressNormalizer addressNormalizer,
      MailAccountSettingsRepository accountRepository,
      EmailAddressRepository addressRepository,
      StalwartManagementClient managementClient,
      Clock clock) {
    this.transactionTemplate = transactionTemplate;
    this.invitationRepository = invitationRepository;
    this.invitationValidator = invitationValidator;
    this.tokenCodec = tokenCodec;
    this.addressNormalizer = addressNormalizer;
    this.accountRepository = accountRepository;
    this.addressRepository = addressRepository;
    this.managementClient = managementClient;
    this.clock = clock;
  }

  public EmailAliasResult create(
      String authenticatedUserId, long mailAccountId, String invitationCode, String localPart) {
    long userId = parseUserId(authenticatedUserId);
    String normalizedAddress = addressNormalizer.normalize(localPart);
    AtomicBoolean remoteAliasAdded = new AtomicBoolean();
    String[] remoteAccountId = new String[1];
    try {
      return transactionTemplate.execute(
          status -> {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
            RegistrationInvitationEntity invitation =
                invitationRepository
                    .findByTokenHashForUpdate(tokenCodec.hash(invitationCode))
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_INVALID));
            invitationValidator.validate(
                invitation, RegistrationInvitationPurpose.EMAIL_ADDRESS, now);
            OwnedMailAccount account =
                accountRepository
                    .findOwnedForUpdate(userId, mailAccountId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (account.status() != MailAccountStatus.ACTIVE
                || account.stalwartAccountId() == null
                || account.stalwartAccountId().isBlank()) {
              throw new BusinessException(ErrorCode.MAIL_ACCOUNT_NOT_READY);
            }
            if (addressRepository.exists(normalizedAddress)) {
              throw new BusinessException(ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE);
            }
            remoteAccountId[0] = account.stalwartAccountId();
            try {
              remoteAliasAdded.set(
                  managementClient.addAccountAlias(account.stalwartAccountId(), normalizedAddress));
            } catch (StalwartProvisioningException exception) {
              log.warn(
                  "Stalwart alias update failed mailAccountId={} failureCode={} diagnostic={}",
                  mailAccountId,
                  exception.failureCode(),
                  exception.diagnostic());
              throw new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE, exception);
            }
            addressRepository.insertAlias(mailAccountId, normalizedAddress, now);
            if (!invitationRepository.markUsed(invitation.getId(), userId, now)) {
              throw new IllegalStateException("Locked invitation could not be consumed");
            }
            return new EmailAliasResult(mailAccountId, normalizedAddress, EmailAddressType.ALIAS);
          });
    } catch (RuntimeException exception) {
      if (remoteAliasAdded.get()) {
        compensateRemoteAlias(remoteAccountId[0], normalizedAddress);
      }
      if (exception instanceof DuplicateKeyException) {
        throw new BusinessException(ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE, exception);
      }
      throw exception;
    }
  }

  public void delete(String authenticatedUserId, long mailAccountId, long addressId) {
    long userId = parseUserId(authenticatedUserId);
    AtomicBoolean remoteAliasRemoved = new AtomicBoolean();
    String[] remoteAccountId = new String[1];
    String[] emailAddress = new String[1];
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            OwnedMailAccount account =
                accountRepository
                    .findOwnedForUpdate(userId, mailAccountId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            EmailAddressEntity address =
                addressRepository
                    .findByIdForUpdate(mailAccountId, addressId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (address.getAddressType() != EmailAddressType.ALIAS) {
              throw new BusinessException(ErrorCode.PRIMARY_EMAIL_ADDRESS_CANNOT_BE_DELETED);
            }
            if (account.status() != MailAccountStatus.ACTIVE
                || account.stalwartAccountId() == null
                || account.stalwartAccountId().isBlank()) {
              throw new BusinessException(ErrorCode.MAIL_ACCOUNT_NOT_READY);
            }
            remoteAccountId[0] = account.stalwartAccountId();
            emailAddress[0] = address.getNormalizedAddress();
            try {
              remoteAliasRemoved.set(
                  managementClient.removeAccountAlias(remoteAccountId[0], emailAddress[0]));
            } catch (StalwartProvisioningException exception) {
              log.warn(
                  "Stalwart alias removal failed mailAccountId={} addressId={} failureCode={} diagnostic={}",
                  mailAccountId,
                  addressId,
                  exception.failureCode(),
                  exception.diagnostic());
              throw new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE, exception);
            }
            if (!addressRepository.deleteAlias(mailAccountId, addressId)) {
              throw new IllegalStateException("Locked email alias could not be deleted");
            }
          });
    } catch (RuntimeException exception) {
      if (remoteAliasRemoved.get()) {
        compensateRemoteAliasRemoval(remoteAccountId[0], emailAddress[0]);
      }
      throw exception;
    }
  }

  private void compensateRemoteAlias(String accountId, String emailAddress) {
    try {
      managementClient.removeAccountAlias(accountId, emailAddress);
    } catch (RuntimeException compensationFailure) {
      log.error("Failed to compensate Stalwart alias update accountId={}", accountId);
    }
  }

  private void compensateRemoteAliasRemoval(String accountId, String emailAddress) {
    try {
      managementClient.addAccountAlias(accountId, emailAddress);
    } catch (RuntimeException compensationFailure) {
      log.error("Failed to compensate Stalwart alias removal accountId={}", accountId);
    }
  }

  private long parseUserId(String subject) {
    try {
      return Long.parseLong(subject);
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED, exception);
    }
  }
}
