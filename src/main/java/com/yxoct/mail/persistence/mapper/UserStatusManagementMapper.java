package com.yxoct.mail.persistence.mapper;

import com.yxoct.mail.persistence.UserStatusMailAccount;
import com.yxoct.mail.persistence.UserStatusTarget;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserStatusManagementMapper {

  @Select(
      """
      SELECT id AS user_id, role, status
      FROM app_user
      WHERE id = #{userId}
      FOR UPDATE
      """)
  UserStatusTarget findUserForUpdate(@Param("userId") long userId);

  @Select(
      """
      SELECT id
      FROM app_user
      WHERE role = 'ADMIN' AND status = 'ACTIVE'
      FOR UPDATE
      """)
  List<Long> findActiveAdministratorIdsForUpdate();

  @Select(
      """
      SELECT ma.id AS mail_account_id,
             ma.stalwart_account_id,
             ma.status
      FROM user_mail_account uma
      JOIN mail_account ma ON ma.id = uma.mail_account_id
      WHERE uma.user_id = #{userId}
        AND uma.account_role = 'OWNER'
      ORDER BY ma.id
      FOR UPDATE
      """)
  List<UserStatusMailAccount> findOwnedMailAccountsForUpdate(@Param("userId") long userId);

  @Update(
      """
      UPDATE app_user
      SET status = 'DISABLED',
          disabled_at = #{disabledAt},
          disabled_by_user_id = #{operatedByUserId},
          disabled_reason = #{reason},
          version = version + 1,
          updated_at = #{disabledAt}
      WHERE id = #{userId} AND status = 'ACTIVE'
      """)
  int disableUser(
      @Param("userId") long userId,
      @Param("operatedByUserId") long operatedByUserId,
      @Param("reason") String reason,
      @Param("disabledAt") LocalDateTime disabledAt);

  @Update(
      """
      UPDATE mail_account ma
      JOIN user_mail_account uma ON uma.mail_account_id = ma.id
      SET ma.status = 'DISABLED',
          ma.version = ma.version + 1,
          ma.updated_at = #{disabledAt}
      WHERE uma.user_id = #{userId}
        AND uma.account_role = 'OWNER'
        AND ma.status <> 'DISABLED'
      """)
  int disableOwnedMailAccounts(
      @Param("userId") long userId, @Param("disabledAt") LocalDateTime disabledAt);

  @Update(
      """
      UPDATE app_user
      SET status = 'ACTIVE',
          disabled_at = NULL,
          disabled_by_user_id = NULL,
          disabled_reason = NULL,
          version = version + 1,
          updated_at = #{enabledAt}
      WHERE id = #{userId} AND status = 'DISABLED'
      """)
  int enableUser(@Param("userId") long userId, @Param("enabledAt") LocalDateTime enabledAt);

  @Update(
      """
      UPDATE mail_account ma
      JOIN user_mail_account uma ON uma.mail_account_id = ma.id
      SET ma.status = CASE
              WHEN ma.stalwart_account_id IS NULL THEN 'PROVISIONING'
              ELSE 'ACTIVE'
          END,
          ma.provisioning_lease_until = NULL,
          ma.next_provisioning_at = CASE
              WHEN ma.stalwart_account_id IS NULL THEN #{enabledAt}
              ELSE ma.next_provisioning_at
          END,
          ma.last_provisioning_error = NULL,
          ma.version = ma.version + 1,
          ma.updated_at = #{enabledAt}
      WHERE uma.user_id = #{userId}
        AND uma.account_role = 'OWNER'
        AND ma.status = 'DISABLED'
      """)
  int enableOwnedMailAccounts(
      @Param("userId") long userId, @Param("enabledAt") LocalDateTime enabledAt);
}
