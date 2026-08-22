package com.yxoct.mail.service;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yxoct.mail.config.EmailRestoreCleanupProperties;
import com.yxoct.mail.persistence.EmailRestoreRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailRestoreCleanupServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");
  private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZONE);

  @Mock private EmailRestoreRepository repository;
  @Mock private EmailRestoreCleanupLeaseCoordinator leaseCoordinator;

  @BeforeEach
  void setUp() {
    lenient().when(leaseCoordinator.tryAcquire(LOCAL_NOW)).thenReturn(true);
  }

  @Test
  void deletesExpiredRecordsInBatches() {
    EmailRestoreCleanupService service = service(true, 2);
    LocalDateTime cutoff = LOCAL_NOW.minusDays(30);
    when(repository.deleteBefore(cutoff, 2)).thenReturn(2, 1);

    service.deleteExpiredRestoreRecords();

    verify(repository, org.mockito.Mockito.times(2)).deleteBefore(cutoff, 2);
    verify(leaseCoordinator, org.mockito.Mockito.times(2)).tryAcquire(LOCAL_NOW);
  }

  @Test
  void doesNothingWhenDisabled() {
    EmailRestoreCleanupService service = service(false, 500);

    service.deleteExpiredRestoreRecords();

    verifyNoInteractions(repository);
    verify(leaseCoordinator, never()).tryAcquire(LOCAL_NOW);
  }

  @Test
  void doesNothingWhenAnotherInstanceOwnsLease() {
    when(leaseCoordinator.tryAcquire(LOCAL_NOW)).thenReturn(false);
    EmailRestoreCleanupService service = service(true, 500);

    service.deleteExpiredRestoreRecords();

    verifyNoInteractions(repository);
  }

  private EmailRestoreCleanupService service(boolean enabled, int batchSize) {
    return new EmailRestoreCleanupService(
        repository,
        new EmailRestoreCleanupProperties(
            enabled, Duration.ofDays(1), Duration.ofDays(30), Duration.ofMinutes(10), batchSize),
        leaseCoordinator,
        Clock.fixed(NOW, ZONE));
  }
}
