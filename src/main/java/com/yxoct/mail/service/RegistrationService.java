package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.RegisterRequest;
import com.yxoct.mail.domain.user.RegistrationResult;
import com.yxoct.mail.persistence.RegistrationInvitationRepository;
import com.yxoct.mail.persistence.entity.RegistrationInvitationPurpose;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

  private final EmailAddressNormalizer emailAddressNormalizer;
  private final InvitationTokenCodec invitationTokenCodec;
  private final RegistrationInvitationRepository invitationRepository;
  private final RegistrationInvitationValidator invitationValidator;
  private final PasswordEncoder passwordEncoder;
  private final LocalRegistrationTransaction registrationTransaction;
  private final Clock clock;

  public RegistrationService(
      EmailAddressNormalizer emailAddressNormalizer,
      InvitationTokenCodec invitationTokenCodec,
      RegistrationInvitationRepository invitationRepository,
      RegistrationInvitationValidator invitationValidator,
      PasswordEncoder passwordEncoder,
      LocalRegistrationTransaction registrationTransaction,
      Clock clock) {
    this.emailAddressNormalizer = emailAddressNormalizer;
    this.invitationTokenCodec = invitationTokenCodec;
    this.invitationRepository = invitationRepository;
    this.invitationValidator = invitationValidator;
    this.passwordEncoder = passwordEncoder;
    this.registrationTransaction = registrationTransaction;
    this.clock = clock;
  }

  public RegistrationResult register(RegisterRequest request) {
    validateRequest(request);
    String normalizedAddress = emailAddressNormalizer.normalize(request.emailLocalPart());
    String invitationTokenHash = invitationTokenCodec.hash(request.invitationCode());
    invitationValidator.validate(
        invitationRepository
            .findByTokenHash(invitationTokenHash)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_INVALID)),
        RegistrationInvitationPurpose.REGISTRATION,
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    String passwordHash = passwordEncoder.encode(request.password());
    return registrationTransaction.register(invitationTokenHash, normalizedAddress, passwordHash);
  }

  private void validateRequest(RegisterRequest request) {
    if (request == null
        || request.invitationCode() == null
        || request.invitationCode().length() < 20
        || request.invitationCode().length() > 200
        || request.password() == null
        || request.password().length() < 12
        || request.password().length() > 128) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
  }
}
