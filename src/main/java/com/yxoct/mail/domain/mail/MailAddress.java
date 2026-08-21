package com.yxoct.mail.domain.mail;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmailAddressResponse", description = "Structured email address")
public record MailAddress(String name, String email) {}
