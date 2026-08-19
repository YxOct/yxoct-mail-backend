package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.mapper.MailAccountSettingsMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MailAccountSettingsRepository {

  private final MailAccountSettingsMapper mapper;

  public MailAccountSettingsRepository(MailAccountSettingsMapper mapper) {
    this.mapper = mapper;
  }

  public Optional<OwnedMailAccount> findOwned(long userId, long mailAccountId) {
    return Optional.ofNullable(mapper.findOwned(userId, mailAccountId));
  }

  public Optional<OwnedMailAccount> findOwnedForUpdate(long userId, long mailAccountId) {
    return Optional.ofNullable(mapper.findOwnedForUpdate(userId, mailAccountId));
  }

  public boolean updateDisplayName(
      long mailAccountId, String displayName, LocalDateTime updatedAt) {
    return mapper.updateDisplayName(mailAccountId, displayName, updatedAt) == 1;
  }
}
