package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.mapper.AuthenticationUserMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class AuthenticationUserRepository {

  private final AuthenticationUserMapper mapper;

  public AuthenticationUserRepository(AuthenticationUserMapper mapper) {
    this.mapper = mapper;
  }

  public Optional<AuthenticatedUser> findByEmailAddress(String normalizedEmailAddress) {
    return Optional.ofNullable(mapper.findByEmailAddress(normalizedEmailAddress));
  }

  public Optional<AuthenticatedUser> findByUserId(long userId) {
    return Optional.ofNullable(mapper.findByUserId(userId));
  }
}
