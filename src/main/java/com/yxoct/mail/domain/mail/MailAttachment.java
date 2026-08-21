package com.yxoct.mail.domain.mail;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmailAttachmentResponse", description = "Email attachment metadata")
public record MailAttachment(
    String partId,
    String blobId,
    @Schema(nullable = true) String name,
    String type,
    long size,
    boolean inline,
    @Schema(nullable = true) String cid) {}
