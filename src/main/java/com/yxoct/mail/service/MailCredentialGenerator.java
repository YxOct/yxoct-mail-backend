package com.yxoct.mail.service;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class MailCredentialGenerator {

  private static final int CREDENTIAL_BYTES = 32;

  private final SecureRandom secureRandom;

  public MailCredentialGenerator(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  public String generate() {
    byte[] credential = new byte[CREDENTIAL_BYTES];
    secureRandom.nextBytes(credential);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(credential);
  }
}
