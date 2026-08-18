package com.yxoct.mail.domain.user;

import com.yxoct.mail.persistence.entity.RegistrationInvitationPurpose;
import jakarta.validation.constraints.NotNull;

public record CreateInvitationRequest(@NotNull RegistrationInvitationPurpose purpose) {}
