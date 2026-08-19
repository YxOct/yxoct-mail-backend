package com.yxoct.mail.persistence.mapper;

import com.yxoct.mail.persistence.AuthenticatedUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthenticationUserMapper {

  @Select(
      """
      SELECT au.id AS user_id,
             ea.normalized_address AS email_address,
             au.password_hash,
             au.status,
             au.role,
             au.version,
             au.must_change_password
      FROM app_user au
      JOIN user_mail_account uma
        ON uma.user_id = au.id
       AND uma.account_role = 'OWNER'
      JOIN email_address ea
        ON ea.mail_account_id = uma.mail_account_id
       AND ea.address_type = 'PRIMARY'
      WHERE ea.normalized_address = #{emailAddress}
      LIMIT 1
      """)
  AuthenticatedUser findByEmailAddress(@Param("emailAddress") String emailAddress);

  @Select(
      """
      SELECT au.id AS user_id,
             ea.normalized_address AS email_address,
             au.password_hash,
             au.status,
             au.role,
             au.version,
             au.must_change_password
      FROM app_user au
      JOIN user_mail_account uma
        ON uma.user_id = au.id
       AND uma.account_role = 'OWNER'
      JOIN email_address ea
        ON ea.mail_account_id = uma.mail_account_id
       AND ea.address_type = 'PRIMARY'
      WHERE au.id = #{userId}
      ORDER BY ea.id
      LIMIT 1
      """)
  AuthenticatedUser findByUserId(@Param("userId") long userId);
}
