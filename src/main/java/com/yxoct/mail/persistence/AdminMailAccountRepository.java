package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.mapper.AdminMailAccountMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class AdminMailAccountRepository {

  private final AdminMailAccountMapper mapper;

  public AdminMailAccountRepository(AdminMailAccountMapper mapper) {
    this.mapper = mapper;
  }

  public long countProvisioningIssues() {
    return mapper.countProvisioningIssues();
  }

  public List<AdminMailAccountProvisioningRecord> findProvisioningIssues(int page, int size) {
    return mapper.findProvisioningIssues((long) (page - 1) * size, size);
  }

  public Optional<AdminMailAccountProvisioningTarget> findForRetryForUpdate(long mailAccountId) {
    return Optional.ofNullable(mapper.findForRetryForUpdate(mailAccountId));
  }

  public boolean scheduleRetry(long mailAccountId, LocalDateTime now) {
    return mapper.scheduleRetry(mailAccountId, now) == 1;
  }

  public void saveRetryAudit(
      long userId, long operatedByUserId, long mailAccountId, LocalDateTime now) {
    if (mapper.saveRetryAudit(userId, operatedByUserId, "mailAccountId=" + mailAccountId, now)
        != 1) {
      throw new IllegalStateException("Could not save provisioning retry audit");
    }
  }
}
