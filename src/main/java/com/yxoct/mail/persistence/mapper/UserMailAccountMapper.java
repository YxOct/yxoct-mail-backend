package com.yxoct.mail.persistence.mapper;

import com.yxoct.mail.persistence.MailAccountCredential;
import com.yxoct.mail.persistence.entity.UserMailAccountEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMailAccountMapper {

  @Insert(
      """
      INSERT INTO user_mail_account (user_id, mail_account_id, account_role)
      VALUES (#{userId}, #{mailAccountId}, #{accountRole})
      """)
  int insert(UserMailAccountEntity relationship);

  @Select("SELECT COUNT(*) FROM user_mail_account WHERE user_id = #{userId}")
  long countByUserId(@Param("userId") long userId);

  @Select(
      """
      SELECT uma.user_id,
             ma.id AS mail_account_id,
             ea.normalized_address AS email_address,
             ma.status,
             ma.credential_ciphertext
      FROM user_mail_account uma
      JOIN mail_account ma ON ma.id = uma.mail_account_id
      JOIN email_address ea
        ON ea.mail_account_id = ma.id
       AND ea.address_type = 'PRIMARY'
      WHERE uma.user_id = #{userId}
        AND uma.account_role = 'OWNER'
      ORDER BY ma.id
      LIMIT 1
      """)
  MailAccountCredential findOwnedPrimaryAccount(@Param("userId") long userId);
}
