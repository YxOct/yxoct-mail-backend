package com.yxoct.mail.domain.user;

import java.time.Instant;

public record CreatedRegistrationInvitation(long id, String token, Instant expiresAt) {}
