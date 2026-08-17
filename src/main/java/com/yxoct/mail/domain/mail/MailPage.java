package com.yxoct.mail.domain.mail;

import java.util.List;

public record MailPage<T>(int page, int size, int total, List<T> items) {}
