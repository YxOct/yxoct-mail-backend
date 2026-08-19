package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.mapper.ScheduledTaskLeaseMapper;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class ScheduledTaskLeaseRepository {

  private final ScheduledTaskLeaseMapper mapper;

  public ScheduledTaskLeaseRepository(ScheduledTaskLeaseMapper mapper) {
    this.mapper = mapper;
  }

  public boolean tryAcquire(
      String taskName, String ownerId, LocalDateTime now, LocalDateTime leaseUntil) {
    if (mapper.updateLease(taskName, ownerId, now, leaseUntil) == 1) {
      return true;
    }
    try {
      return mapper.insertLease(taskName, ownerId, now, leaseUntil) == 1;
    } catch (DuplicateKeyException exception) {
      return false;
    }
  }
}
