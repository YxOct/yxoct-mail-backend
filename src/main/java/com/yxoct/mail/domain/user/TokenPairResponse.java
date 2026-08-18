package com.yxoct.mail.domain.user;

public record TokenPairResponse(
    String accessToken,
    String tokenType,
    long accessExpiresIn,
    String refreshToken,
    long refreshExpiresIn) {}
