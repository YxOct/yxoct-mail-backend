package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.AdminUserPage;
import com.yxoct.mail.domain.user.AdminUserSummary;
import com.yxoct.mail.persistence.AdminUserRecord;
import com.yxoct.mail.persistence.AdminUserRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminUserService {

  private final AdminUserRepository repository;

  public AdminUserService(AdminUserRepository repository) {
    this.repository = repository;
  }

  public AdminUserPage list(int page, int size) {
    return new AdminUserPage(
        page,
        size,
        repository.count(),
        repository.findPage(page, size).stream().map(this::map).toList());
  }

  public AdminUserSummary get(long userId) {
    return repository
        .findById(userId)
        .map(this::map)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private AdminUserSummary map(AdminUserRecord user) {
    return new AdminUserSummary(
        user.userId(),
        user.primaryEmailAddress(),
        user.displayName(),
        user.role(),
        user.userStatus(),
        user.mailAccountId(),
        user.mailAccountStatus(),
        user.createdAt());
  }
}
