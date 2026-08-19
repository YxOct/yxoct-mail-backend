package com.yxoct.mail.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yxoct.mail.persistence.entity.EmailAddressEntity;
import com.yxoct.mail.persistence.entity.EmailAddressType;
import com.yxoct.mail.persistence.mapper.EmailAddressMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class EmailAliasRepository {

  private final EmailAddressMapper mapper;

  public EmailAliasRepository(EmailAddressMapper mapper) {
    this.mapper = mapper;
  }

  public boolean exists(String normalizedAddress) {
    return mapper.selectCount(
            Wrappers.<EmailAddressEntity>lambdaQuery()
                .eq(EmailAddressEntity::getNormalizedAddress, normalizedAddress))
        > 0;
  }

  public void insert(long mailAccountId, String normalizedAddress, LocalDateTime now) {
    EmailAddressEntity entity = new EmailAddressEntity();
    entity.setMailAccountId(mailAccountId);
    entity.setAddress(normalizedAddress);
    entity.setNormalizedAddress(normalizedAddress);
    entity.setAddressType(EmailAddressType.ALIAS);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    mapper.insert(entity);
  }
}
