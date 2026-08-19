package com.yxoct.mail.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yxoct.mail.persistence.entity.EmailAddressEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmailAddressMapper extends BaseMapper<EmailAddressEntity> {

  @Select(
      """
      SELECT id, mail_account_id, address, normalized_address, address_type, created_at, updated_at
      FROM email_address
      WHERE id = #{addressId}
        AND mail_account_id = #{mailAccountId}
      FOR UPDATE
      """)
  EmailAddressEntity findByIdForUpdate(
      @Param("mailAccountId") long mailAccountId, @Param("addressId") long addressId);
}
