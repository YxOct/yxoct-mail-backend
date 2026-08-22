package com.yxoct.mail.service;

import com.yxoct.mail.config.EmailRestoreCleanupProperties;
import com.yxoct.mail.persistence.ScheduledTaskLeaseRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EmailRestoreCleanupLeaseCoordinator {

  static final String TASK_NAME = "email-restore-cleanup";

  private final ScheduledTaskLeaseRepository repository;
  private final EmailRestoreCleanupProperties properties;
  private final String ownerId = UUID.randomUUID().toString();

  public EmailRestoreCleanupLeaseCoordinator(
      ScheduledTaskLeaseRepository repository, EmailRestoreCleanupProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  public boolean tryAcquire(LocalDateTime now) {
    return repository.tryAcquire(TASK_NAME, ownerId, now, now.plus(properties.leaseDuration()));
  }
}
