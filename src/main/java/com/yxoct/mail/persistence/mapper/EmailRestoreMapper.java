package com.yxoct.mail.persistence.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmailRestoreMapper {

  @Select(
      """
      SELECT COUNT(*)
      FROM email_restore_record
      WHERE account_id = #{accountId} AND email_id = #{emailId}
      """)
  int countRecord(@Param("accountId") String accountId, @Param("emailId") String emailId);

  @Select(
      """
      SELECT mailbox_id
      FROM email_restore_mailbox
      WHERE account_id = #{accountId} AND email_id = #{emailId}
      ORDER BY mailbox_id
      """)
  List<String> findMailboxIds(
      @Param("accountId") String accountId, @Param("emailId") String emailId);

  @Insert(
      """
      INSERT INTO email_restore_record (account_id, email_id)
      VALUES (#{accountId}, #{emailId})
      """)
  int insertRecord(@Param("accountId") String accountId, @Param("emailId") String emailId);

  @Insert(
      """
      INSERT INTO email_restore_mailbox (account_id, email_id, mailbox_id)
      VALUES (#{accountId}, #{emailId}, #{mailboxId})
      """)
  int insertMailbox(
      @Param("accountId") String accountId,
      @Param("emailId") String emailId,
      @Param("mailboxId") String mailboxId);

  @Delete(
      """
      DELETE FROM email_restore_record
      WHERE account_id = #{accountId} AND email_id = #{emailId}
      """)
  int deleteRecord(@Param("accountId") String accountId, @Param("emailId") String emailId);
}
