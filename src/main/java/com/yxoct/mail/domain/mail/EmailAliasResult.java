package com.yxoct.mail.domain.mail;

import com.yxoct.mail.persistence.entity.EmailAddressType;

public record EmailAliasResult(
    long mailAccountId, String emailAddress, EmailAddressType addressType) {}
