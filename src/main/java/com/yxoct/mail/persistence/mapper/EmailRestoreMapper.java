package com.yxoct.mail.persistence.mapper;

import com.yxoct.mail.persistence.EmailRestoreRecordKey;
import java.time.LocalDateTime;
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

  @Select(
      """
      SELECT account_id, email_id
      FROM email_restore_record
      WHERE deleted_at < #{cutoff}
      ORDER BY deleted_at, account_id, email_id
      LIMIT #{batchSize}
      """)
  List<EmailRestoreRecordKey> findRecordsBefore(
      @Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
