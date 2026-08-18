package com.yxoct.mail.domain.mail;

public record MailAttachment(
    String partId,
    String blobId,
    String name,
    String type,
    long size,
    boolean inline,
    String cid) {}
