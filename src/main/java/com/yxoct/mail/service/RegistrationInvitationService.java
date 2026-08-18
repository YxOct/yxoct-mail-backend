package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.config.RegistrationProperties;
import com.yxoct.mail.domain.user.CreatedRegistrationInvitation;
import com.yxoct.mail.persistence.RegistrationInvitationRepository;
import com.yxoct.mail.persistence.entity.RegistrationInvitationEntity;
import com.yxoct.mail.persistence.entity.RegistrationInvitationPurpose;
import com.yxoct.mail.persistence.entity.RegistrationInvitationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationInvitationService {

  private final RegistrationInvitationRepository repository;
  private final InvitationTokenCodec tokenCodec;
  private final RegistrationProperties properties;
  private final Clock clock;

  public RegistrationInvitationService(
      RegistrationInvitationRepository repository,
      InvitationTokenCodec tokenCodec,
      RegistrationProperties properties,
      Clock clock) {
    this.repository = repository;
    this.tokenCodec = tokenCodec;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public CreatedRegistrationInvitation create() {
    return create(RegistrationInvitationPurpose.REGISTRATION);
  }

  @Transactional
  public CreatedRegistrationInvitation create(RegistrationInvitationPurpose purpose) {
    String token = tokenCodec.generate();
    Instant expiresAt = clock.instant().plus(properties.invitationTtl());

    RegistrationInvitationEntity invitation = new RegistrationInvitationEntity();
    invitation.setTokenHash(tokenCodec.hash(token));
    invitation.setStatus(RegistrationInvitationStatus.PENDING);
    invitation.setPurpose(purpose);
    invitation.setExpiresAt(LocalDateTime.ofInstant(expiresAt, clock.getZone()));
    repository.save(invitation);

    return new CreatedRegistrationInvitation(invitation.getId(), token, expiresAt);
  }

  @Transactional
  public void revoke(long invitationId) {
    if (!repository.revoke(invitationId)) {
      throw new BusinessException(ErrorCode.INVITATION_INVALID);
    }
  }
}
