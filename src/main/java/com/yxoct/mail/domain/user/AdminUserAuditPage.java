package com.yxoct.mail.domain.user;

import java.util.List;

public record AdminUserAuditPage(int page, int size, long total, List<AdminUserAuditEntry> items) {}
