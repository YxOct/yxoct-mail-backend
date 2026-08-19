package com.yxoct.mail.domain.mail;

import java.util.List;

public record AdminMailAccountProvisioningPage(
    int page, int size, long total, List<AdminMailAccountProvisioningEntry> items) {}
