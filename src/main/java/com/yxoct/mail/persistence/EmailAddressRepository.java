package com.yxoct.mail.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yxoct.mail.persistence.entity.EmailAddressEntity;
import com.yxoct.mail.persistence.entity.EmailAddressType;
import com.yxoct.mail.persistence.mapper.EmailAddressMapper;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class EmailAddressRepository {

  private final EmailAddressMapper mapper;

  public EmailAddressRepository(EmailAddressMapper mapper) {
    this.mapper = mapper;
  }

  public boolean exists(String normalizedAddress) {
    return mapper.selectCount(
            Wrappers.<EmailAddressEntity>lambdaQuery()
                .eq(EmailAddressEntity::getNormalizedAddress, normalizedAddress))
        > 0;
  }

  public List<EmailAddressEntity> findAllByMailAccountId(long mailAccountId) {
    return mapper
        .selectList(
            Wrappers.<EmailAddressEntity>lambdaQuery()
                .eq(EmailAddressEntity::getMailAccountId, mailAccountId))
        .stream()
        .sorted(
            Comparator.comparingInt(
                    (EmailAddressEntity address) ->
                        address.getAddressType() == EmailAddressType.PRIMARY ? 0 : 1)
                .thenComparing(EmailAddressEntity::getId))
        .toList();
  }

  public void insertAlias(long mailAccountId, String normalizedAddress, LocalDateTime now) {
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
