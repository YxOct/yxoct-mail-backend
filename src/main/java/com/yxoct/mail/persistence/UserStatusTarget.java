package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;

public record UserStatusTarget(long userId, UserRole role, UserStatus status, long version) {}
