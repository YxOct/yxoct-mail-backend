package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.mapper.AdminUserMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUserRepository {

  private final AdminUserMapper mapper;

  public AdminUserRepository(AdminUserMapper mapper) {
    this.mapper = mapper;
  }

  public long count() {
    return mapper.countUsers();
  }

  public List<AdminUserRecord> findPage(int page, int size) {
    return mapper.findUsers((long) (page - 1) * size, size);
  }

  public Optional<AdminUserRecord> findById(long userId) {
    return Optional.ofNullable(mapper.findUser(userId));
  }

  public long countAudits(long userId) {
    return mapper.countUserAudits(userId);
  }

  public List<UserAuditRecord> findAudits(long userId, int page, int size) {
    return mapper.findUserAudits(userId, (long) (page - 1) * size, size);
  }
}
