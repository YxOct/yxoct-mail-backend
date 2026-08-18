package com.yxoct.mail.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yxoct.mail.persistence.MailAccountProvisioningTask;
import com.yxoct.mail.persistence.entity.MailAccountEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MailAccountMapper extends BaseMapper<MailAccountEntity> {

  @Select(
      """
      SELECT id
      FROM mail_account
      WHERE status IN ('PROVISIONING', 'FAILED')
        AND next_provisioning_at <= #{now}
        AND (provisioning_lease_until IS NULL OR provisioning_lease_until <= #{now})
      ORDER BY next_provisioning_at, id
      LIMIT #{limit}
      """)
  List<Long> findProvisioningCandidates(@Param("now") LocalDateTime now, @Param("limit") int limit);

  @Update(
      """
      UPDATE mail_account
      SET status = 'PROVISIONING',
          provisioning_attempts = provisioning_attempts + 1,
          provisioning_lease_until = #{leaseUntil},
          updated_at = #{now}
      WHERE id = #{id}
        AND status IN ('PROVISIONING', 'FAILED')
        AND next_provisioning_at <= #{now}
        AND (provisioning_lease_until IS NULL OR provisioning_lease_until <= #{now})
      """)
  int claimProvisioning(
      @Param("id") long id,
      @Param("now") LocalDateTime now,
      @Param("leaseUntil") LocalDateTime leaseUntil);

  @Select(
      """
      SELECT ma.id AS account_id,
             ea.normalized_address AS email_address,
             ma.stalwart_account_id,
             ma.credential_ciphertext,
             ma.provisioning_attempts
      FROM mail_account ma
      JOIN email_address ea
        ON ea.mail_account_id = ma.id
       AND ea.address_type = 'PRIMARY'
      WHERE ma.id = #{id}
      """)
  MailAccountProvisioningTask findProvisioningTask(@Param("id") long id);

  @Update(
      """
      UPDATE mail_account
      SET credential_ciphertext = #{ciphertext}, updated_at = #{now}
      WHERE id = #{id}
        AND credential_ciphertext IS NULL
        AND status = 'PROVISIONING'
      """)
  int saveCredential(
      @Param("id") long id,
      @Param("ciphertext") String ciphertext,
      @Param("now") LocalDateTime now);

  @Update(
      """
      UPDATE mail_account
      SET stalwart_account_id = #{stalwartAccountId},
          status = 'ACTIVE',
          provisioning_lease_until = NULL,
          last_provisioning_error = NULL,
          updated_at = #{now}
      WHERE id = #{id} AND status = 'PROVISIONING'
      """)
  int markProvisioningSucceeded(
      @Param("id") long id,
      @Param("stalwartAccountId") String stalwartAccountId,
      @Param("now") LocalDateTime now);

  @Update(
      """
      UPDATE mail_account
      SET status = 'FAILED',
          provisioning_lease_until = NULL,
          next_provisioning_at = #{nextAttemptAt},
          last_provisioning_error = #{failureCode},
          updated_at = #{now}
      WHERE id = #{id} AND status = 'PROVISIONING'
      """)
  int markProvisioningFailed(
      @Param("id") long id,
      @Param("failureCode") String failureCode,
      @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
      @Param("now") LocalDateTime now);
}
