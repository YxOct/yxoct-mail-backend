package com.yxoct.mail.domain.mail;

import jakarta.validation.constraints.NotNull;

public record UpdateReadStatusRequest(@NotNull Boolean read) {}
