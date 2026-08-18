package com.yxoct.mail.service;

import com.yxoct.mail.config.StalwartProvisioningProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class MailCredentialCipher {

  private static final String PREFIX = "v1:";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom secureRandom;

  public MailCredentialCipher(
      StalwartProvisioningProperties properties, SecureRandom secureRandom) {
    byte[] keyBytes =
        properties.enabled() ? properties.decodedCredentialEncryptionKey() : new byte[32];
    this.key = new SecretKeySpec(keyBytes, "AES");
    this.secureRandom = secureRandom;
  }

  public String encrypt(String plaintext) {
    if (plaintext == null || plaintext.isBlank()) {
      throw new IllegalArgumentException("Credential must not be blank");
    }
    byte[] nonce = new byte[NONCE_BYTES];
    secureRandom.nextBytes(nonce);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] payload =
          ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
      return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Could not encrypt mail credential", exception);
    }
  }

  public String decrypt(String ciphertext) {
    if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
      throw new IllegalStateException("Unsupported mail credential format");
    }
    try {
      byte[] payload = Base64.getUrlDecoder().decode(ciphertext.substring(PREFIX.length()));
      if (payload.length <= NONCE_BYTES) {
        throw new IllegalStateException("Invalid encrypted mail credential");
      }
      byte[] nonce = new byte[NONCE_BYTES];
      byte[] encrypted = new byte[payload.length - NONCE_BYTES];
      System.arraycopy(payload, 0, nonce, 0, nonce.length);
      System.arraycopy(payload, nonce.length, encrypted, 0, encrypted.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalStateException("Could not decrypt mail credential", exception);
    }
  }
}
