package com.yxoct.mail.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class InvitationTokenCodec {

  private static final int TOKEN_BYTES = 32;

  private final SecureRandom secureRandom;

  public InvitationTokenCodec(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  public String generate() {
    byte[] token = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(token);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
  }

  public String hash(String token) {
    if (token == null || token.isBlank()) {
      return "";
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
