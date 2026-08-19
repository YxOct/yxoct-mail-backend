package com.yxoct.mail.persistence.mapper;

import com.yxoct.mail.persistence.AdminMailAccountDriftTarget;
import com.yxoct.mail.persistence.AdminMailAccountProvisioningRecord;
import com.yxoct.mail.persistence.AdminMailAccountProvisioningTarget;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminMailAccountMapper {

  @Select(
      """
      SELECT COUNT(*)
      FROM mail_account
      WHERE status IN ('PROVISIONING', 'FAILED')
      """)
  long countProvisioningIssues();

  @Select(
      """
      SELECT ma.id AS mail_account_id,
             uma.user_id,
             ea.normalized_address AS email_address,
             ma.status,
             ma.provisioning_attempts,
             ma.last_provisioning_error,
             ma.next_provisioning_at,
             ma.provisioning_lease_until,
             ma.updated_at
      FROM mail_account ma
      JOIN user_mail_account uma
        ON uma.mail_account_id = ma.id
       AND uma.account_role = 'OWNER'
      JOIN email_address ea
        ON ea.mail_account_id = ma.id
       AND ea.address_type = 'PRIMARY'
      WHERE ma.status IN ('PROVISIONING', 'FAILED')
      ORDER BY ma.next_provisioning_at, ma.id
      LIMIT #{limit} OFFSET #{offset}
      """)
  List<AdminMailAccountProvisioningRecord> findProvisioningIssues(
      @Param("offset") long offset, @Param("limit") int limit);

  @Select(
      """
      SELECT ma.id AS mail_account_id,
             uma.user_id,
             ma.status,
             ma.provisioning_lease_until
      FROM mail_account ma
      JOIN user_mail_account uma
        ON uma.mail_account_id = ma.id
       AND uma.account_role = 'OWNER'
      WHERE ma.id = #{mailAccountId}
      FOR UPDATE
      """)
  AdminMailAccountProvisioningTarget findForRetryForUpdate(
      @Param("mailAccountId") long mailAccountId);

  @Update(
      """
      UPDATE mail_account
      SET status = 'FAILED',
          provisioning_lease_until = NULL,
          next_provisioning_at = #{now},
          version = version + 1,
          updated_at = #{now}
      WHERE id = #{mailAccountId}
        AND status IN ('PROVISIONING', 'FAILED')
      """)
  int scheduleRetry(@Param("mailAccountId") long mailAccountId, @Param("now") LocalDateTime now);

  @Insert(
      """
      INSERT INTO user_status_audit
          (user_id, action, reason, operated_by_user_id, created_at)
      VALUES
          (#{userId}, 'MAIL_ACCOUNT_PROVISIONING_RETRY_REQUESTED', #{reason},
           #{operatedByUserId}, #{now})
      """)
  int saveRetryAudit(
      @Param("userId") long userId,
      @Param("operatedByUserId") long operatedByUserId,
      @Param("reason") String reason,
      @Param("now") LocalDateTime now);

  @Select(
      """
      SELECT ma.id AS mail_account_id,
             uma.user_id,
             ea.normalized_address AS email_address,
             ma.display_name,
             ma.stalwart_account_id,
             ma.status AS local_status,
             r.drift_type
      FROM mail_account_reconciliation r
      JOIN mail_account ma ON ma.id = r.mail_account_id
      JOIN user_mail_account uma
        ON uma.mail_account_id = ma.id
       AND uma.account_role = 'OWNER'
      JOIN email_address ea
        ON ea.mail_account_id = ma.id
       AND ea.address_type = 'PRIMARY'
      WHERE ma.id = #{mailAccountId}
        AND r.drift_type <> 'NONE'
      FOR UPDATE
      """)
  AdminMailAccountDriftTarget findDriftForUpdate(@Param("mailAccountId") long mailAccountId);

  @Update(
      """
      UPDATE mail_account
      SET stalwart_account_id = NULL,
          status = 'FAILED',
          provisioning_lease_until = NULL,
          next_provisioning_at = #{now},
          last_provisioning_error = 'REMOTE_ACCOUNT_MISSING',
          version = version + 1,
          updated_at = #{now}
      WHERE id = #{mailAccountId}
        AND status = 'ACTIVE'
      """)
  int scheduleMissingAccountReprovisioning(
      @Param("mailAccountId") long mailAccountId, @Param("now") LocalDateTime now);

  @Insert(
      """
      INSERT INTO user_status_audit
          (user_id, action, reason, operated_by_user_id, created_at)
      VALUES
          (#{userId}, 'MAIL_ACCOUNT_DRIFT_REPAIR_REQUESTED', #{reason},
           #{operatedByUserId}, #{now})
      """)
  int saveDriftRepairAudit(
      @Param("userId") long userId,
      @Param("operatedByUserId") long operatedByUserId,
      @Param("reason") String reason,
      @Param("now") LocalDateTime now);
}
