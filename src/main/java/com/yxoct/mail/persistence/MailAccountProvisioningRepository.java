package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.mapper.MailAccountMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MailAccountProvisioningRepository {

  private final MailAccountMapper mapper;

  public MailAccountProvisioningRepository(MailAccountMapper mapper) {
    this.mapper = mapper;
  }

  public List<Long> findCandidates(LocalDateTime now, int limit) {
    return mapper.findProvisioningCandidates(now, limit);
  }

  public boolean claim(long accountId, LocalDateTime now, LocalDateTime leaseUntil) {
    return mapper.claimProvisioning(accountId, now, leaseUntil) == 1;
  }

  public MailAccountProvisioningTask findTask(long accountId) {
    return mapper.findProvisioningTask(accountId);
  }

  public boolean saveCredential(long accountId, String ciphertext, LocalDateTime now) {
    return mapper.saveCredential(accountId, ciphertext, now) == 1;
  }

  public boolean markSucceeded(long accountId, String stalwartAccountId, LocalDateTime now) {
    return mapper.markProvisioningSucceeded(accountId, stalwartAccountId, now) == 1;
  }

  public boolean markFailed(
      long accountId, String failureCode, LocalDateTime nextAttemptAt, LocalDateTime now) {
    return mapper.markProvisioningFailed(accountId, failureCode, nextAttemptAt, now) == 1;
  }
}
