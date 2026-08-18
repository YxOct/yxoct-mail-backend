package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.mapper.UserMailAccountMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MailAccountCredentialRepository {

  private final UserMailAccountMapper mapper;

  public MailAccountCredentialRepository(UserMailAccountMapper mapper) {
    this.mapper = mapper;
  }

  public Optional<MailAccountCredential> findOwnedPrimaryAccount(long userId) {
    return Optional.ofNullable(mapper.findOwnedPrimaryAccount(userId));
  }
}
