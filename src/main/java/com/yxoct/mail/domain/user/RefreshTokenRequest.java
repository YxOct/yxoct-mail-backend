package com.yxoct.mail.domain.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RefreshTokenRequest(
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]{43}")
        String refreshToken) {

  @Override
  public String toString() {
    return "RefreshTokenRequest[refreshToken=***]";
  }
}
