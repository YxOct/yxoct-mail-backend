package com.yxoct.mail.domain.mail;

import java.util.List;

public record AdminMailAccountDriftPage(
    int page, int size, long total, List<AdminMailAccountDriftEntry> items) {}
