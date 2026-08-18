package com.yxoct.mail.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yxoct.mail.domain.user.RegistrationResult;
import com.yxoct.mail.persistence.entity.AppUserEntity;
import com.yxoct.mail.persistence.entity.EmailAddressEntity;
import com.yxoct.mail.persistence.entity.EmailAddressType;
import com.yxoct.mail.persistence.entity.MailAccountEntity;
import com.yxoct.mail.persistence.entity.MailAccountRole;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.persistence.entity.UserMailAccountEntity;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;
import com.yxoct.mail.persistence.mapper.AppUserMapper;
import com.yxoct.mail.persistence.mapper.EmailAddressMapper;
import com.yxoct.mail.persistence.mapper.MailAccountMapper;
import com.yxoct.mail.persistence.mapper.UserMailAccountMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class UserRegistrationRepository {

  private final AppUserMapper appUserMapper;
  private final MailAccountMapper mailAccountMapper;
  private final EmailAddressMapper emailAddressMapper;
  private final UserMailAccountMapper userMailAccountMapper;

  public UserRegistrationRepository(
      AppUserMapper appUserMapper,
      MailAccountMapper mailAccountMapper,
      EmailAddressMapper emailAddressMapper,
      UserMailAccountMapper userMailAccountMapper) {
    this.appUserMapper = appUserMapper;
    this.mailAccountMapper = mailAccountMapper;
    this.emailAddressMapper = emailAddressMapper;
    this.userMailAccountMapper = userMailAccountMapper;
  }

  public boolean emailAddressExists(String normalizedAddress) {
    return emailAddressMapper.selectCount(
            Wrappers.<EmailAddressEntity>lambdaQuery()
                .eq(EmailAddressEntity::getNormalizedAddress, normalizedAddress))
        > 0;
  }

  public RegistrationResult create(
      String normalizedAddress,
      String displayName,
      String passwordHash,
      LocalDateTime provisioningAt) {
    AppUserEntity user = new AppUserEntity();
    user.setPasswordHash(passwordHash);
    user.setStatus(UserStatus.ACTIVE);
    user.setRole(UserRole.USER);
    appUserMapper.insert(user);

    MailAccountEntity mailAccount = new MailAccountEntity();
    mailAccount.setDisplayName(displayName);
    mailAccount.setStatus(MailAccountStatus.PROVISIONING);
    mailAccount.setNextProvisioningAt(provisioningAt);
    mailAccountMapper.insert(mailAccount);

    EmailAddressEntity emailAddress = new EmailAddressEntity();
    emailAddress.setMailAccountId(mailAccount.getId());
    emailAddress.setAddress(normalizedAddress);
    emailAddress.setNormalizedAddress(normalizedAddress);
    emailAddress.setAddressType(EmailAddressType.PRIMARY);
    emailAddressMapper.insert(emailAddress);

    UserMailAccountEntity ownership = new UserMailAccountEntity();
    ownership.setUserId(user.getId());
    ownership.setMailAccountId(mailAccount.getId());
    ownership.setAccountRole(MailAccountRole.OWNER);
    userMailAccountMapper.insert(ownership);

    return new RegistrationResult(
        user.getId(),
        mailAccount.getId(),
        normalizedAddress,
        displayName,
        MailAccountStatus.PROVISIONING);
  }
}
