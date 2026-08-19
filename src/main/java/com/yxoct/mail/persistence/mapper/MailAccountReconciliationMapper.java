package com.yxoct.mail.persistence.mapper;

import com.yxoct.mail.persistence.AdminMailAccountDriftRecord;
import com.yxoct.mail.persistence.MailAccountReconciliationCandidate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MailAccountReconciliationMapper {

  @Select(
      """
      SELECT ma.id AS mail_account_id,
             uma.user_id,
             ea.normalized_address AS email_address,
             ma.stalwart_account_id,
             ma.status
      FROM mail_account ma
      JOIN user_mail_account uma
        ON uma.mail_account_id = ma.id
       AND uma.account_role = 'OWNER'
      JOIN email_address ea
        ON ea.mail_account_id = ma.id
       AND ea.address_type = 'PRIMARY'
      WHERE ma.status IN ('ACTIVE', 'DISABLED')
        AND ma.stalwart_account_id IS NOT NULL
      ORDER BY COALESCE(
          (SELECT checked_at
           FROM mail_account_reconciliation r
           WHERE r.mail_account_id = ma.id),
          '1970-01-01 00:00:00'),
          ma.id
      LIMIT #{limit}
      """)
  List<MailAccountReconciliationCandidate> findCandidates(@Param("limit") int limit);

  @Delete("DELETE FROM mail_account_reconciliation WHERE mail_account_id = #{mailAccountId}")
  int deleteResult(@Param("mailAccountId") long mailAccountId);

  @Insert(
      """
      INSERT INTO mail_account_reconciliation
          (mail_account_id, drift_type, last_error, checked_at)
      VALUES
          (#{mailAccountId}, #{driftType}, #{lastError}, #{checkedAt})
      """)
  int saveResult(
      @Param("mailAccountId") long mailAccountId,
      @Param("driftType") String driftType,
      @Param("lastError") String lastError,
      @Param("checkedAt") LocalDateTime checkedAt);

  @Select("SELECT COUNT(*) FROM mail_account_reconciliation WHERE drift_type <> 'NONE'")
  long countDrifts();

  @Select(
      """
      SELECT ma.id AS mail_account_id,
             uma.user_id,
             ea.normalized_address AS email_address,
             ma.status AS local_status,
             ma.stalwart_account_id,
             r.drift_type,
             r.last_error,
             r.checked_at
      FROM mail_account_reconciliation r
      JOIN mail_account ma ON ma.id = r.mail_account_id
      JOIN user_mail_account uma
        ON uma.mail_account_id = ma.id
       AND uma.account_role = 'OWNER'
      JOIN email_address ea
        ON ea.mail_account_id = ma.id
       AND ea.address_type = 'PRIMARY'
      WHERE r.drift_type <> 'NONE'
      ORDER BY r.checked_at DESC, ma.id
      LIMIT #{limit} OFFSET #{offset}
      """)
  List<AdminMailAccountDriftRecord> findDrifts(
      @Param("offset") long offset, @Param("limit") int limit);
}
