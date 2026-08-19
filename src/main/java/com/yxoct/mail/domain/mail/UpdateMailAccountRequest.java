package com.yxoct.mail.domain.mail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMailAccountRequest(
    @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\p{Cc}]*") String displayName) {}
