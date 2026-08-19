package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import com.yxoct.mail.config.StalwartProvisioningProperties;
import com.yxoct.mail.monitoring.MailOperationalMetrics;
import com.yxoct.mail.persistence.MailAccountProvisioningRepository;
import com.yxoct.mail.persistence.MailAccountProvisioningTask;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MailAccountProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(MailAccountProvisioningService.class);

  private final MailAccountProvisioningRepository repository;
  private final StalwartManagementClient stalwartManagementClient;
  private final MailCredentialGenerator credentialGenerator;
  private final MailCredentialCipher credentialCipher;
  private final StalwartProvisioningProperties properties;
  private final Clock clock;
  private final MailOperationalMetrics metrics;

  public MailAccountProvisioningService(
      MailAccountProvisioningRepository repository,
      StalwartManagementClient stalwartManagementClient,
      MailCredentialGenerator credentialGenerator,
      MailCredentialCipher credentialCipher,
      StalwartProvisioningProperties properties,
      MailOperationalMetrics metrics,
      Clock clock) {
    this.repository = repository;
    this.stalwartManagementClient = stalwartManagementClient;
    this.credentialGenerator = credentialGenerator;
    this.credentialCipher = credentialCipher;
    this.properties = properties;
    this.metrics = metrics;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${stalwart.provisioning.scan-interval:10s}")
  public void provisionPendingAccounts() {
    if (!properties.enabled()) {
      return;
    }
    LocalDateTime now = now();
    for (Long accountId : repository.findCandidates(now, properties.batchSize())) {
      provision(accountId);
    }
  }

  public void provision(long accountId) {
    if (!properties.enabled()) {
      return;
    }
    LocalDateTime claimedAt = now();
    if (!repository.claim(accountId, claimedAt, claimedAt.plus(properties.leaseDuration()))) {
      metrics.recordProvisioning("claim_skipped");
      return;
    }

    int attempt = 1;
    try {
      MailAccountProvisioningTask task = repository.findTask(accountId);
      if (task == null) {
        throw new StalwartProvisioningException("LOCAL_ACCOUNT_NOT_FOUND");
      }
      attempt = task.provisioningAttempts();
      if (task.stalwartAccountId() != null && !task.stalwartAccountId().isBlank()) {
        requireUpdated(repository.markSucceeded(accountId, task.stalwartAccountId(), now()));
        metrics.recordProvisioning("succeeded");
        return;
      }
      String ciphertext = ensureCredential(task);
      String password = credentialCipher.decrypt(ciphertext);
      String stalwartAccountId =
          stalwartManagementClient.ensureAccount(task.emailAddress(), password, task.displayName());
      requireUpdated(repository.markSucceeded(accountId, stalwartAccountId, now()));
      metrics.recordProvisioning("succeeded");
      log.info("Provisioned Stalwart mail account localAccountId={}", accountId);
    } catch (StalwartProvisioningException exception) {
      if (exception.diagnostic() != null) {
        log.warn(
            "Stalwart provisioning diagnostic localAccountId={} failureCode={} diagnostic={}",
            accountId,
            exception.failureCode(),
            exception.diagnostic());
      }
      recordFailure(accountId, attempt, exception.failureCode());
    } catch (RuntimeException exception) {
      recordFailure(accountId, attempt, "UNEXPECTED_PROVISIONING_FAILURE");
      log.error("Unexpected Stalwart provisioning failure localAccountId={}", accountId, exception);
    }
  }

  private String ensureCredential(MailAccountProvisioningTask task) {
    if (task.credentialCiphertext() != null && !task.credentialCiphertext().isBlank()) {
      return task.credentialCiphertext();
    }
    String ciphertext = credentialCipher.encrypt(credentialGenerator.generate());
    if (!repository.saveCredential(task.accountId(), ciphertext, now())) {
      MailAccountProvisioningTask refreshed = repository.findTask(task.accountId());
      if (refreshed == null
          || refreshed.credentialCiphertext() == null
          || refreshed.credentialCiphertext().isBlank()) {
        throw new IllegalStateException("Could not persist generated mail credential");
      }
      return refreshed.credentialCiphertext();
    }
    return ciphertext;
  }

  private void recordFailure(long accountId, int attempt, String failureCode) {
    LocalDateTime failedAt = now();
    Duration retryDelay = retryDelay(attempt);
    repository.markFailed(accountId, failureCode, failedAt.plus(retryDelay), failedAt);
    metrics.recordProvisioning("failed");
    log.warn(
        "Stalwart provisioning failed localAccountId={} failureCode={} retryIn={}",
        accountId,
        failureCode,
        retryDelay);
  }

  private Duration retryDelay(int attempt) {
    Duration delay = properties.initialRetryDelay();
    for (int index = 1;
        index < attempt && delay.compareTo(properties.maxRetryDelay()) < 0;
        index++) {
      if (delay.compareTo(properties.maxRetryDelay().dividedBy(2)) > 0) {
        return properties.maxRetryDelay();
      }
      delay = delay.multipliedBy(2);
    }
    return delay.compareTo(properties.maxRetryDelay()) > 0 ? properties.maxRetryDelay() : delay;
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
  }

  private void requireUpdated(boolean updated) {
    if (!updated) {
      throw new IllegalStateException("Provisioning state changed concurrently");
    }
  }
}
