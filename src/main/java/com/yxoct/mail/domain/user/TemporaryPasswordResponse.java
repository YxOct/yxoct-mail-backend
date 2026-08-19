package com.yxoct.mail.domain.user;

import io.swagger.v3.oas.annotations.media.Schema;

public record TemporaryPasswordResponse(
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, format = "password") String temporaryPassword,
    boolean mustChangePassword) {}
