package com.yxoct.mail.domain.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Email @Size(max = 254) String emailAddress,
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @NotBlank
        @Size(max = 128)
        String password) {

  @Override
  public String toString() {
    return "LoginRequest[emailAddress=" + emailAddress + ", password=***]";
  }
}
