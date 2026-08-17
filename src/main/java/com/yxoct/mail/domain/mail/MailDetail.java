package com.yxoct.mail.domain.mail;

import java.util.List;
import java.util.Map;

public record MailDetail(
    String id,
    String subject,
    String preview,
    String receivedAt,
    Map<String, Object> bodyValues,
    List<Map<String, Object>> from,
    List<Map<String, Object>> to) {}
