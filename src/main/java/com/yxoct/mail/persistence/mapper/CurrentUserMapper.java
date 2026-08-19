package com.yxoct.mail.persistence.mapper;

import com.yxoct.mail.persistence.CurrentUserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CurrentUserMapper {

  @Select(
      """
      SELECT au.id AS user_id,
             ma.id AS mail_account_id,
             ea.normalized_address AS email_address,
             ma.display_name,
             au.role,
             au.status,
             au.must_change_password,
             ma.status AS mail_account_status
      FROM app_user au
      JOIN user_mail_account uma
        ON uma.user_id = au.id
       AND uma.account_role = 'OWNER'
      JOIN mail_account ma
        ON ma.id = uma.mail_account_id
      JOIN email_address ea
        ON ea.mail_account_id = ma.id
       AND ea.address_type = 'PRIMARY'
      WHERE au.id = #{userId}
      ORDER BY ma.id, ea.id
      LIMIT 1
      """)
  CurrentUserAccount findOwnedPrimaryAccount(@Param("userId") long userId);
}
