package com.yxoct.mail.domain.user;

import com.yxoct.mail.persistence.entity.RegistrationInvitationPurpose;
import com.yxoct.mail.persistence.entity.RegistrationInvitationStatus;
import java.time.Instant;

public record RegistrationInvitationSummary(
    long id,
    RegistrationInvitationStatus status,
    RegistrationInvitationPurpose purpose,
    Instant expiresAt,
    Long usedByUserId,
    Instant usedAt,
    Long createdByUserId,
    Long revokedByUserId,
    Instant revokedAt,
    Instant createdAt) {}
