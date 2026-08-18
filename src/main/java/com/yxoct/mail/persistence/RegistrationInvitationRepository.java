package com.yxoct.mail.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yxoct.mail.persistence.entity.RegistrationInvitationEntity;
import com.yxoct.mail.persistence.mapper.RegistrationInvitationMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RegistrationInvitationRepository {

  private final RegistrationInvitationMapper mapper;

  public RegistrationInvitationRepository(RegistrationInvitationMapper mapper) {
    this.mapper = mapper;
  }

  public RegistrationInvitationEntity save(RegistrationInvitationEntity invitation) {
    mapper.insert(invitation);
    return invitation;
  }

  public Optional<RegistrationInvitationEntity> findByTokenHashForUpdate(String tokenHash) {
    return Optional.ofNullable(mapper.findByTokenHashForUpdate(tokenHash));
  }

  public Optional<RegistrationInvitationEntity> findByTokenHash(String tokenHash) {
    return Optional.ofNullable(
        mapper.selectOne(
            Wrappers.<RegistrationInvitationEntity>lambdaQuery()
                .eq(RegistrationInvitationEntity::getTokenHash, tokenHash)));
  }

  public boolean markUsed(long id, long userId, LocalDateTime usedAt) {
    return mapper.markUsed(id, userId, usedAt) == 1;
  }

  public boolean revoke(long id, Long userId, LocalDateTime revokedAt) {
    return mapper.revoke(id, userId, revokedAt) == 1;
  }

  public List<RegistrationInvitationEntity> findRecent(int limit) {
    return mapper.findRecent(limit);
  }
}
