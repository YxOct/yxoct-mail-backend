package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.config.RegistrationProperties;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EmailAddressNormalizer {

  private static final Pattern LOCAL_PART_PATTERN =
      Pattern.compile("[a-z0-9](?:[a-z0-9._-]{0,62}[a-z0-9])?");

  private final String mailDomain;
  private final Set<String> reservedLocalParts;

  public EmailAddressNormalizer(RegistrationProperties properties) {
    this.mailDomain = properties.mailDomain();
    this.reservedLocalParts =
        properties.reservedLocalParts().stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
  }

  public String normalize(String localPart) {
    if (localPart == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }

    String normalizedLocalPart = localPart.toLowerCase(Locale.ROOT);
    if (!LOCAL_PART_PATTERN.matcher(normalizedLocalPart).matches()
        || normalizedLocalPart.contains("..")) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
    if (reservedLocalParts.contains(normalizedLocalPart)) {
      throw new BusinessException(ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE);
    }
    String address = normalizedLocalPart + "@" + mailDomain;
    if (address.length() > 254) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
    return address;
  }
}
