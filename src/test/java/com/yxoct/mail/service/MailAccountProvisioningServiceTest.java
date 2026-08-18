package com.yxoct.mail.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import com.yxoct.mail.config.StalwartProvisioningProperties;
import com.yxoct.mail.persistence.MailAccountProvisioningRepository;
import com.yxoct.mail.persistence.MailAccountProvisioningTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailAccountProvisioningServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
  private static final LocalDateTime NOW_UTC = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

  @Mock private MailAccountProvisioningRepository repository;
  @Mock private StalwartManagementClient managementClient;
  @Mock private MailCredentialGenerator credentialGenerator;
  @Mock private MailCredentialCipher credentialCipher;

  private MailAccountProvisioningService service;

  @BeforeEach
  void setUp() {
    service =
        new MailAccountProvisioningService(
            repository,
            managementClient,
            credentialGenerator,
            credentialCipher,
            properties(true),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void claimsGeneratesCredentialAndActivatesAccount() {
    when(repository.claim(42, NOW_UTC, NOW_UTC.plusMinutes(1))).thenReturn(true);
    when(repository.findTask(42))
        .thenReturn(new MailAccountProvisioningTask(42, "alice@yxoct.com", null, null, 1));
    when(credentialGenerator.generate()).thenReturn("internal-secret");
    when(credentialCipher.encrypt("internal-secret")).thenReturn("v1:ciphertext");
    when(repository.saveCredential(42, "v1:ciphertext", NOW_UTC)).thenReturn(true);
    when(credentialCipher.decrypt("v1:ciphertext")).thenReturn("internal-secret");
    when(managementClient.ensureAccount(42, "alice@yxoct.com", "internal-secret"))
        .thenReturn("stalwart-1");
    when(repository.markSucceeded(42, "stalwart-1", NOW_UTC)).thenReturn(true);

    service.provision(42);

    verify(repository).markSucceeded(42, "stalwart-1", NOW_UTC);
  }

  @Test
  void recordsFailureWithExponentialRetryDelay() {
    when(repository.claim(42, NOW_UTC, NOW_UTC.plusMinutes(1))).thenReturn(true);
    when(repository.findTask(42))
        .thenReturn(
            new MailAccountProvisioningTask(42, "alice@yxoct.com", null, "v1:ciphertext", 3));
    when(credentialCipher.decrypt("v1:ciphertext")).thenReturn("internal-secret");
    when(managementClient.ensureAccount(42, "alice@yxoct.com", "internal-secret"))
        .thenThrow(new StalwartProvisioningException("MANAGEMENT_REQUEST_FAILED"));

    service.provision(42);

    verify(repository)
        .markFailed(42, "MANAGEMENT_REQUEST_FAILED", NOW_UTC.plus(Duration.ofMinutes(2)), NOW_UTC);
  }

  @Test
  void recordsFailureWhenClaimedAccountNoLongerExists() {
    when(repository.claim(42, NOW_UTC, NOW_UTC.plusMinutes(1))).thenReturn(true);
    when(repository.findTask(42)).thenReturn(null);

    service.provision(42);

    verify(repository).markFailed(42, "LOCAL_ACCOUNT_NOT_FOUND", NOW_UTC.plusSeconds(30), NOW_UTC);
  }

  @Test
  void doesNothingWhenProvisioningIsDisabled() {
    service =
        new MailAccountProvisioningService(
            repository,
            managementClient,
            credentialGenerator,
            credentialCipher,
            properties(false),
            Clock.fixed(NOW, ZoneOffset.UTC));

    service.provision(42);
    service.provisionPendingAccounts();

    verifyNoInteractions(repository, managementClient, credentialGenerator, credentialCipher);
  }

  private StalwartProvisioningProperties properties(boolean enabled) {
    return new StalwartProvisioningProperties(
        enabled,
        enabled ? "API-key" : "",
        enabled ? "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" : "",
        Duration.ofSeconds(10),
        Duration.ofMinutes(1),
        Duration.ofSeconds(30),
        Duration.ofHours(1),
        20);
  }
}
