package com.yxoct.mail.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.registration")
public record RegistrationProperties(
    @NotBlank String mailDomain,
    @NotNull Duration invitationTtl,
    @NotEmpty Set<String> reservedLocalParts) {

  private static final Pattern DOMAIN_PATTERN =
      Pattern.compile("(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}");

  @AssertTrue(message = "mail-domain must be a valid lowercase domain")
  public boolean isMailDomainValid() {
    return mailDomain == null || DOMAIN_PATTERN.matcher(mailDomain).matches();
  }

  @AssertTrue(message = "invitation-ttl must be greater than zero")
  public boolean isInvitationTtlValid() {
    return invitationTtl == null || (!invitationTtl.isZero() && !invitationTtl.isNegative());
  }
}
