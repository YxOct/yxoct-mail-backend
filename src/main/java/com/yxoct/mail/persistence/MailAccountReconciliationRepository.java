package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.MailAccountDriftType;
import com.yxoct.mail.persistence.mapper.MailAccountReconciliationMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MailAccountReconciliationRepository {

  private final MailAccountReconciliationMapper mapper;

  public MailAccountReconciliationRepository(MailAccountReconciliationMapper mapper) {
    this.mapper = mapper;
  }

  public List<MailAccountReconciliationCandidate> findCandidates(int limit) {
    return mapper.findCandidates(limit);
  }

  @Transactional
  public void saveResult(
      long mailAccountId,
      MailAccountDriftType driftType,
      String lastError,
      LocalDateTime checkedAt) {
    mapper.deleteResult(mailAccountId);
    String storedType = driftType == null ? "NONE" : driftType.name();
    if (mapper.saveResult(mailAccountId, storedType, lastError, checkedAt) != 1) {
      throw new IllegalStateException("Could not save mail account reconciliation result");
    }
  }

  public long countDrifts() {
    return mapper.countDrifts();
  }

  public List<AdminMailAccountDriftRecord> findDrifts(int page, int size) {
    return mapper.findDrifts((long) (page - 1) * size, size);
  }

  public void clearResult(long mailAccountId) {
    mapper.deleteResult(mailAccountId);
  }
}
