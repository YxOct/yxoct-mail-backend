package com.yxoct.mail.domain.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisableUserRequest(@NotBlank @Size(max = 500) String reason) {}
