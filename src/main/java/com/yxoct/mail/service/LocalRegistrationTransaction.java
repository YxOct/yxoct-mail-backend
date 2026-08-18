package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.RegistrationResult;
import com.yxoct.mail.persistence.RegistrationInvitationRepository;
import com.yxoct.mail.persistence.UserRegistrationRepository;
import com.yxoct.mail.persistence.entity.RegistrationInvitationEntity;
import com.yxoct.mail.persistence.entity.RegistrationInvitationPurpose;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalRegistrationTransaction {

  private final RegistrationInvitationRepository invitationRepository;
  private final UserRegistrationRepository userRegistrationRepository;
  private final RegistrationInvitationValidator invitationValidator;
  private final Clock clock;

  public LocalRegistrationTransaction(
      RegistrationInvitationRepository invitationRepository,
      UserRegistrationRepository userRegistrationRepository,
      RegistrationInvitationValidator invitationValidator,
      Clock clock) {
    this.invitationRepository = invitationRepository;
    this.userRegistrationRepository = userRegistrationRepository;
    this.invitationValidator = invitationValidator;
    this.clock = clock;
  }

  @Transactional
  public RegistrationResult register(
      String invitationTokenHash, String normalizedAddress, String passwordHash) {
    RegistrationInvitationEntity invitation =
        invitationRepository
            .findByTokenHashForUpdate(invitationTokenHash)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_INVALID));
    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    invitationValidator.validate(invitation, RegistrationInvitationPurpose.REGISTRATION, now);

    if (userRegistrationRepository.emailAddressExists(normalizedAddress)) {
      throw new BusinessException(ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE);
    }

    try {
      RegistrationResult result =
          userRegistrationRepository.create(normalizedAddress, passwordHash, now);
      if (!invitationRepository.markUsed(invitation.getId(), result.userId(), now)) {
        throw new IllegalStateException("Locked invitation could not be consumed");
      }
      return result;
    } catch (DuplicateKeyException exception) {
      throw new BusinessException(ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE, exception);
    }
  }
}
