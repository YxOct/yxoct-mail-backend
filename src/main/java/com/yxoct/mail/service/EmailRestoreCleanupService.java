package com.yxoct.mail.service;

import com.yxoct.mail.config.EmailRestoreCleanupProperties;
import com.yxoct.mail.persistence.EmailRestoreRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class EmailRestoreCleanupService {

  private static final Logger log = LoggerFactory.getLogger(EmailRestoreCleanupService.class);
  private final EmailRestoreRepository repository;
  private final EmailRestoreCleanupProperties properties;
  private final EmailRestoreCleanupLeaseCoordinator leaseCoordinator;
  private final Clock clock;

  public EmailRestoreCleanupService(
      EmailRestoreRepository repository,
      EmailRestoreCleanupProperties properties,
      EmailRestoreCleanupLeaseCoordinator leaseCoordinator,
      Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.leaseCoordinator = leaseCoordinator;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${mail.restore-cleanup.scan-interval}")
  public void deleteExpiredRestoreRecords() {
    if (!properties.enabled()) {
      return;
    }
    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    if (!leaseCoordinator.tryAcquire(now)) {
      return;
    }

    LocalDateTime cutoff = now.minus(properties.retention());
    int deletedCount = 0;
    int batchCount;
    do {
      batchCount = repository.deleteBefore(cutoff, properties.batchSize());
      deletedCount += batchCount;
      if (batchCount == properties.batchSize()) {
        LocalDateTime leaseRenewedAt = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        if (!leaseCoordinator.tryAcquire(leaseRenewedAt)) {
          break;
        }
      }
    } while (batchCount == properties.batchSize());

    if (deletedCount > 0) {
      log.info("Deleted expired email restore records count={}", deletedCount);
    }
  }
}
