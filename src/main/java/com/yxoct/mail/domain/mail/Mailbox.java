package com.yxoct.mail.domain.mail;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MailboxResponse", description = "Mailbox returned by the JMAP mail service")
public record Mailbox(String id, String name, String role) {}
