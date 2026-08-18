package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.RefreshTokenSessionEntity;
import com.yxoct.mail.persistence.mapper.RefreshTokenSessionMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenSessionRepository {

  private final RefreshTokenSessionMapper mapper;

  public RefreshTokenSessionRepository(RefreshTokenSessionMapper mapper) {
    this.mapper = mapper;
  }

  public void save(RefreshTokenSessionEntity session) {
    if (mapper.insert(session) != 1) {
      throw new IllegalStateException("Could not save refresh token session");
    }
  }

  public Optional<RefreshTokenSessionEntity> findByTokenHashForUpdate(String tokenHash) {
    return Optional.ofNullable(mapper.findByTokenHashForUpdate(tokenHash));
  }

  public boolean revoke(long id, LocalDateTime revokedAt) {
    return mapper.revoke(id, revokedAt) == 1;
  }

  public void revokeByTokenHash(String tokenHash, LocalDateTime revokedAt) {
    mapper.revokeByTokenHash(tokenHash, revokedAt);
  }
}
