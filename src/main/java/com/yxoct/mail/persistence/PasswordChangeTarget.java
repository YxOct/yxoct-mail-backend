package com.yxoct.mail.persistence;

public record PasswordChangeTarget(long userId, String passwordHash, long version) {}
