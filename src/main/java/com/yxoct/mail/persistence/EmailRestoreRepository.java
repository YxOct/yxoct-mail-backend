package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.mapper.EmailRestoreMapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EmailRestoreRepository {

  private final EmailRestoreMapper mapper;

  public EmailRestoreRepository(EmailRestoreMapper mapper) {
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public Optional<List<String>> findMailboxIds(String accountId, String emailId) {
    if (mapper.countRecord(accountId, emailId) == 0) {
      return Optional.empty();
    }

    List<String> mailboxIds = mapper.findMailboxIds(accountId, emailId);
    if (mailboxIds.isEmpty()) {
      throw new IllegalStateException("Restore record has no mailbox locations");
    }
    return Optional.of(List.copyOf(mailboxIds));
  }

  @Transactional
  public boolean saveIfAbsent(String accountId, String emailId, List<String> mailboxIds) {
    validateMailboxIds(mailboxIds);
    try {
      mapper.insertRecord(accountId, emailId);
    } catch (DuplicateKeyException exception) {
      return false;
    }

    mailboxIds.forEach(mailboxId -> mapper.insertMailbox(accountId, emailId, mailboxId));
    return true;
  }

  @Transactional
  public void deleteAll(String accountId, List<String> emailIds) {
    emailIds.forEach(emailId -> mapper.deleteRecord(accountId, emailId));
  }

  @Transactional
  public int deleteBefore(LocalDateTime cutoff, int batchSize) {
    if (cutoff == null || batchSize < 1 || batchSize > 1000) {
      throw new IllegalArgumentException("Cutoff and batch size must be valid");
    }
    List<EmailRestoreRecordKey> records = mapper.findRecordsBefore(cutoff, batchSize);
    records.forEach(record -> mapper.deleteRecord(record.accountId(), record.emailId()));
    return records.size();
  }

  private void validateMailboxIds(List<String> mailboxIds) {
    if (mailboxIds == null
        || mailboxIds.isEmpty()
        || mailboxIds.stream().anyMatch(mailboxId -> mailboxId == null || mailboxId.isBlank())
        || new HashSet<>(mailboxIds).size() != mailboxIds.size()) {
      throw new IllegalArgumentException("Mailbox ids must be non-empty and unique");
    }
  }
}
