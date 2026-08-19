package com.yxoct.mail.domain.mail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEmailAliasRequest(
    @NotBlank @Size(max = 100) String invitationCode,
    @NotBlank @Size(max = 64) String emailLocalPart) {}
