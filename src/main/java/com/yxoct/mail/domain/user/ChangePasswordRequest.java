package com.yxoct.mail.domain.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @NotBlank
        @Size(max = 128)
        String currentPassword,
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @NotBlank
        @Size(min = 12, max = 128)
        String newPassword) {

  @Override
  public String toString() {
    return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
  }
}
