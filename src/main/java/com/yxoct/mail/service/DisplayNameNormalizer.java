package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class DisplayNameNormalizer {

  public String normalizeOptional(String requestedDisplayName, String fallback) {
    return requestedDisplayName == null || requestedDisplayName.isBlank()
        ? fallback
        : normalizeRequired(requestedDisplayName);
  }

  public String normalizeRequired(String requestedDisplayName) {
    if (requestedDisplayName == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
    String displayName = requestedDisplayName.strip();
    if (displayName.isEmpty()
        || displayName.length() > 100
        || displayName.codePoints().anyMatch(Character::isISOControl)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
    return displayName;
  }
}
