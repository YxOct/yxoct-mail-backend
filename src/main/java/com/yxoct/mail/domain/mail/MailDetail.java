package com.yxoct.mail.domain.mail;

import java.util.List;

public record MailDetail(
    String id,
    String subject,
    String preview,
    String receivedAt,
    List<MailAddress> from,
    List<MailAddress> to,
    String body) {}
