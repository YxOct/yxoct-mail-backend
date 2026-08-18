package com.yxoct.mail.domain.user;

public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {}
