package com.yxoct.mail.persistence.mapper;

import com.yxoct.mail.persistence.OwnedMailAccount;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MailAccountSettingsMapper {

  @Select(
      """
      SELECT ma.id AS mail_account_id,
             ma.stalwart_account_id,
             ma.display_name,
             ma.status
      FROM user_mail_account uma
      JOIN mail_account ma ON ma.id = uma.mail_account_id
      WHERE uma.user_id = #{userId}
        AND uma.mail_account_id = #{mailAccountId}
        AND uma.account_role = 'OWNER'
      FOR UPDATE
      """)
  OwnedMailAccount findOwnedForUpdate(
      @Param("userId") long userId, @Param("mailAccountId") long mailAccountId);

  @Update(
      """
      UPDATE mail_account
      SET display_name = #{displayName}, updated_at = #{updatedAt}
      WHERE id = #{mailAccountId}
      """)
  int updateDisplayName(
      @Param("mailAccountId") long mailAccountId,
      @Param("displayName") String displayName,
      @Param("updatedAt") LocalDateTime updatedAt);
}
