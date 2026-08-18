package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.config.RegistrationProperties;
import com.yxoct.mail.domain.user.CreatedRegistrationInvitation;
import com.yxoct.mail.domain.user.RegistrationInvitationSummary;
import com.yxoct.mail.persistence.RegistrationInvitationRepository;
import com.yxoct.mail.persistence.entity.RegistrationInvitationEntity;
import com.yxoct.mail.persistence.entity.RegistrationInvitationPurpose;
import com.yxoct.mail.persistence.entity.RegistrationInvitationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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
    return create(purpose, null);
  }

  @Transactional
  public CreatedRegistrationInvitation create(
      RegistrationInvitationPurpose purpose, Long createdByUserId) {
    String token = tokenCodec.generate();
    Instant expiresAt = clock.instant().plus(properties.invitationTtl());

    RegistrationInvitationEntity invitation = new RegistrationInvitationEntity();
    invitation.setTokenHash(tokenCodec.hash(token));
    invitation.setStatus(RegistrationInvitationStatus.PENDING);
    invitation.setPurpose(purpose);
    invitation.setExpiresAt(LocalDateTime.ofInstant(expiresAt, clock.getZone()));
    invitation.setCreatedByUserId(createdByUserId);
    repository.save(invitation);

    return new CreatedRegistrationInvitation(invitation.getId(), token, expiresAt);
  }

  @Transactional
  public void revoke(long invitationId) {
    revoke(invitationId, null);
  }

  @Transactional
  public void revoke(long invitationId, Long revokedByUserId) {
    LocalDateTime revokedAt = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    if (!repository.revoke(invitationId, revokedByUserId, revokedAt)) {
      throw new BusinessException(ErrorCode.INVITATION_INVALID);
    }
  }

  @Transactional(readOnly = true)
  public List<RegistrationInvitationSummary> list(int limit) {
    return repository.findRecent(limit).stream()
        .map(
            invitation ->
                new RegistrationInvitationSummary(
                    invitation.getId(),
                    invitation.getStatus(),
                    invitation.getPurpose(),
                    toInstant(invitation.getExpiresAt()),
                    invitation.getUsedByUserId(),
                    toInstant(invitation.getUsedAt()),
                    invitation.getCreatedByUserId(),
                    invitation.getRevokedByUserId(),
                    toInstant(invitation.getRevokedAt()),
                    toInstant(invitation.getCreatedAt())))
        .toList();
  }

  private Instant toInstant(LocalDateTime value) {
    return value == null ? null : value.atZone(clock.getZone()).toInstant();
  }
}
