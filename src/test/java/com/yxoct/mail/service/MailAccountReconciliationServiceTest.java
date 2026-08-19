package com.yxoct.mail.service;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yxoct.mail.client.stalwart.StalwartAccountMetadata;
import com.yxoct.mail.client.stalwart.StalwartAccountSnapshot;
import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import com.yxoct.mail.config.StalwartProvisioningProperties;
import com.yxoct.mail.config.StalwartReconciliationProperties;
import com.yxoct.mail.monitoring.MailOperationalMetrics;
import com.yxoct.mail.persistence.MailAccountReconciliationCandidate;
import com.yxoct.mail.persistence.MailAccountReconciliationRepository;
import com.yxoct.mail.persistence.entity.MailAccountDriftType;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailAccountReconciliationServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final Instant INSTANT = Instant.parse("2026-08-19T12:00:00Z");
  private static final LocalDateTime NOW = LocalDateTime.ofInstant(INSTANT, ZONE);

  @Mock private MailAccountReconciliationRepository repository;
  @Mock private StalwartManagementClient managementClient;
  @Mock private ReconciliationLeaseCoordinator leaseCoordinator;
  @Mock private MailOperationalMetrics metrics;
  private MailAccountReconciliationService service;

  @BeforeEach
  void setUp() {
    service = service(true);
    lenient().when(leaseCoordinator.tryAcquire(NOW)).thenReturn(true);
  }

  @Test
  void recordsMissingRemoteAccount() {
    var candidate = candidate(MailAccountStatus.ACTIVE);
    when(repository.findCandidates(20)).thenReturn(List.of(candidate));
    when(managementClient.inspectAccount("account-9")).thenReturn(Optional.empty());

    service.reconcileAccounts();

    verify(repository).saveResult(9, MailAccountDriftType.REMOTE_ACCOUNT_MISSING, null, NOW);
  }

  @Test
  void usesConfiguredBatchSize() {
    service = service(true, 37);
    when(repository.findCandidates(37)).thenReturn(List.of());

    service.reconcileAccounts();

    verify(repository).findCandidates(37);
  }

  @Test
  void recordsEnabledStateMismatch() {
    var candidate = candidate(MailAccountStatus.DISABLED);
    when(repository.findCandidates(20)).thenReturn(List.of(candidate));
    when(managementClient.inspectAccount("account-9"))
        .thenReturn(Optional.of(new StalwartAccountSnapshot("account-9", true)));
    service.reconcileAccounts();

    verify(repository).saveResult(9, MailAccountDriftType.ENABLED_STATE_MISMATCH, null, NOW);
  }

  @Test
  void clearsPreviousDriftWhenStatesMatch() {
    var candidate = candidate(MailAccountStatus.ACTIVE);
    when(repository.findCandidates(20)).thenReturn(List.of(candidate));
    when(managementClient.inspectAccount("account-9"))
        .thenReturn(Optional.of(new StalwartAccountSnapshot("account-9", true)));
    when(managementClient.inspectAccountMetadata("account-9", "yxoct.com"))
        .thenReturn(new StalwartAccountMetadata("Alice", java.util.Set.of()));
    when(repository.findExpectedAliases(9)).thenReturn(List.of());

    service.reconcileAccounts();

    verify(repository).saveResult(9, null, null, NOW);
  }

  @Test
  void recordsDisplayNameMismatch() {
    var candidate = candidate(MailAccountStatus.ACTIVE);
    when(repository.findCandidates(20)).thenReturn(List.of(candidate));
    when(managementClient.inspectAccount("account-9"))
        .thenReturn(Optional.of(new StalwartAccountSnapshot("account-9", true)));
    when(managementClient.inspectAccountMetadata("account-9", "yxoct.com"))
        .thenReturn(new StalwartAccountMetadata("Wrong", java.util.Set.of()));

    service.reconcileAccounts();

    verify(repository).saveResult(9, MailAccountDriftType.DISPLAY_NAME_MISMATCH, null, NOW);
  }

  @Test
  void recordsAliasMismatch() {
    var candidate = candidate(MailAccountStatus.ACTIVE);
    when(repository.findCandidates(20)).thenReturn(List.of(candidate));
    when(managementClient.inspectAccount("account-9"))
        .thenReturn(Optional.of(new StalwartAccountSnapshot("account-9", true)));
    when(managementClient.inspectAccountMetadata("account-9", "yxoct.com"))
        .thenReturn(new StalwartAccountMetadata("Alice", java.util.Set.of("old@yxoct.com")));
    when(repository.findExpectedAliases(9)).thenReturn(List.of("alias@yxoct.com"));

    service.reconcileAccounts();

    verify(repository).saveResult(9, MailAccountDriftType.ALIAS_MISMATCH, null, NOW);
  }

  @Test
  void recordsInspectionFailureWithoutLeakingDiagnostic() {
    var candidate = candidate(MailAccountStatus.ACTIVE);
    when(repository.findCandidates(20)).thenReturn(List.of(candidate));
    when(managementClient.inspectAccount("account-9"))
        .thenThrow(new StalwartProvisioningException("MANAGEMENT_REQUEST_FAILED", "secret"));

    service.reconcileAccounts();

    verify(repository)
        .saveResult(9, MailAccountDriftType.INSPECTION_FAILED, "MANAGEMENT_REQUEST_FAILED", NOW);
  }

  @Test
  void doesNothingWhenProvisioningIntegrationIsDisabled() {
    service = service(false);

    service.reconcileAccounts();

    verifyNoInteractions(repository, managementClient, leaseCoordinator, metrics);
  }

  @Test
  void doesNothingWhenAnotherInstanceOwnsTheLease() {
    when(leaseCoordinator.tryAcquire(NOW)).thenReturn(false);

    service.reconcileAccounts();

    verifyNoInteractions(repository, managementClient);
  }

  private MailAccountReconciliationService service(boolean enabled) {
    return service(enabled, 20);
  }

  private MailAccountReconciliationService service(boolean enabled, int batchSize) {
    return new MailAccountReconciliationService(
        repository,
        managementClient,
        new StalwartProvisioningProperties(
            enabled,
            enabled ? "API-key" : "",
            enabled ? "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" : "",
            Duration.ofSeconds(10),
            Duration.ofMinutes(1),
            Duration.ofSeconds(30),
            Duration.ofHours(1),
            20),
        new StalwartReconciliationProperties(
            Duration.ofMinutes(5), Duration.ofMinutes(10), batchSize),
        leaseCoordinator,
        metrics,
        Clock.fixed(INSTANT, ZONE));
  }

  private MailAccountReconciliationCandidate candidate(MailAccountStatus status) {
    return new MailAccountReconciliationCandidate(
        9, 7, "alice@yxoct.com", "Alice", "account-9", status);
  }
}
