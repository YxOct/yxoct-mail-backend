package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.mapper.CurrentUserMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class CurrentUserRepository {

  private final CurrentUserMapper mapper;

  public CurrentUserRepository(CurrentUserMapper mapper) {
    this.mapper = mapper;
  }

  public Optional<CurrentUserAccount> findOwnedPrimaryAccount(long userId) {
    return Optional.ofNullable(mapper.findOwnedPrimaryAccount(userId));
  }
}
