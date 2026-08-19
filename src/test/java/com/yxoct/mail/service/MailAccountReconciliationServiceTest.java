package com.yxoct.mail.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yxoct.mail.client.stalwart.StalwartAccountSnapshot;
import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import com.yxoct.mail.config.StalwartProvisioningProperties;
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
  private MailAccountReconciliationService service;

  @BeforeEach
  void setUp() {
    service = service(true);
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

    service.reconcileAccounts();

    verify(repository).saveResult(9, null, null, NOW);
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

    verifyNoInteractions(repository, managementClient);
  }

  private MailAccountReconciliationService service(boolean enabled) {
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
        Clock.fixed(INSTANT, ZONE));
  }

  private MailAccountReconciliationCandidate candidate(MailAccountStatus status) {
    return new MailAccountReconciliationCandidate(9, 7, "alice@yxoct.com", "account-9", status);
  }
}
