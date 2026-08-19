package com.yxoct.mail.domain.user;

import java.util.List;

public record AdminUserPage(int page, int size, long total, List<AdminUserSummary> items) {}
