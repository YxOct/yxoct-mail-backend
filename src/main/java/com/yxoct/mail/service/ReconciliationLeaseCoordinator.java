package com.yxoct.mail.service;

import com.yxoct.mail.config.StalwartReconciliationProperties;
import com.yxoct.mail.persistence.ScheduledTaskLeaseRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationLeaseCoordinator {

  static final String TASK_NAME = "stalwart-account-reconciliation";

  private final ScheduledTaskLeaseRepository repository;
  private final StalwartReconciliationProperties properties;
  private final String ownerId = UUID.randomUUID().toString();

  public ReconciliationLeaseCoordinator(
      ScheduledTaskLeaseRepository repository, StalwartReconciliationProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  public boolean tryAcquire(LocalDateTime now) {
    return repository.tryAcquire(TASK_NAME, ownerId, now, now.plus(properties.leaseDuration()));
  }
}
