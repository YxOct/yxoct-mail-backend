package com.yxoct.mail.domain.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) @NotBlank @Size(min = 20, max = 200)
        String invitationCode,
    @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9](?:[A-Za-z0-9._-]{0,62}[A-Za-z0-9])?")
        String emailLocalPart,
    @Size(max = 100) @Pattern(regexp = "[^\\p{Cc}]*") String displayName,
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @NotBlank
        @Size(min = 12, max = 128)
        String password) {

  @Override
  public String toString() {
    return "RegisterRequest[invitationCode=***, emailLocalPart="
        + emailLocalPart
        + ", displayName="
        + displayName
        + ", password=***]";
  }
}
