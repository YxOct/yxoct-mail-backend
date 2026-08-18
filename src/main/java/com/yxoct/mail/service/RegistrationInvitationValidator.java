package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.persistence.entity.RegistrationInvitationEntity;
import com.yxoct.mail.persistence.entity.RegistrationInvitationPurpose;
import com.yxoct.mail.persistence.entity.RegistrationInvitationStatus;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class RegistrationInvitationValidator {

  public void validate(
      RegistrationInvitationEntity invitation,
      RegistrationInvitationPurpose expectedPurpose,
      LocalDateTime now) {
    if (invitation.getPurpose() != expectedPurpose) {
      throw new BusinessException(ErrorCode.INVITATION_INVALID);
    }
    if (invitation.getStatus() == RegistrationInvitationStatus.USED) {
      throw new BusinessException(ErrorCode.INVITATION_ALREADY_USED);
    }
    if (invitation.getStatus() == RegistrationInvitationStatus.REVOKED) {
      throw new BusinessException(ErrorCode.INVITATION_REVOKED);
    }
    if (!invitation.getExpiresAt().isAfter(now)) {
      throw new BusinessException(ErrorCode.INVITATION_EXPIRED);
    }
  }
}
