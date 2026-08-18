package com.yxoct.mail.domain.mail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchUpdateStarStatusRequest(
    @NotEmpty @Size(max = 100) List<@NotBlank String> ids, @NotNull Boolean starred) {}
