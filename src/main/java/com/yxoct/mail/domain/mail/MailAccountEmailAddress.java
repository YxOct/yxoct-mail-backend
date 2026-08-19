package com.yxoct.mail.domain.mail;

import com.yxoct.mail.persistence.entity.EmailAddressType;

public record MailAccountEmailAddress(long id, String emailAddress, EmailAddressType addressType) {}
