package com.yxoct.mail.persistence.mapper;

import com.yxoct.mail.persistence.AdminUserRecord;
import com.yxoct.mail.persistence.UserAuditRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper {

  String USER_SELECT =
      """
      SELECT au.id AS user_id,
             ea.normalized_address AS primary_email_address,
             ma.display_name,
             au.role,
             au.status AS user_status,
             ma.id AS mail_account_id,
             ma.status AS mail_account_status,
             au.created_at
      FROM app_user au
      LEFT JOIN user_mail_account uma
        ON uma.user_id = au.id
       AND uma.account_role = 'OWNER'
       AND uma.mail_account_id = (
           SELECT MIN(owned.mail_account_id)
           FROM user_mail_account owned
           WHERE owned.user_id = au.id
             AND owned.account_role = 'OWNER'
       )
      LEFT JOIN mail_account ma ON ma.id = uma.mail_account_id
      LEFT JOIN email_address ea
        ON ea.mail_account_id = ma.id
       AND ea.address_type = 'PRIMARY'
      """;

  @Select("SELECT COUNT(*) FROM app_user")
  long countUsers();

  @Select(USER_SELECT + " ORDER BY au.id DESC LIMIT #{limit} OFFSET #{offset}")
  List<AdminUserRecord> findUsers(@Param("offset") long offset, @Param("limit") int limit);

  @Select(USER_SELECT + " WHERE au.id = #{userId}")
  AdminUserRecord findUser(@Param("userId") long userId);

  @Select("SELECT COUNT(*) FROM user_status_audit WHERE user_id = #{userId}")
  long countUserAudits(@Param("userId") long userId);

  @Select(
      """
      SELECT audit.id AS audit_id,
             audit.action,
             audit.reason,
             audit.operated_by_user_id,
             operator_address.normalized_address AS operated_by_email_address,
             audit.created_at
      FROM user_status_audit audit
      LEFT JOIN user_mail_account operator_account
        ON operator_account.user_id = audit.operated_by_user_id
       AND operator_account.account_role = 'OWNER'
       AND operator_account.mail_account_id = (
           SELECT MIN(owned.mail_account_id)
           FROM user_mail_account owned
           WHERE owned.user_id = audit.operated_by_user_id
             AND owned.account_role = 'OWNER'
       )
      LEFT JOIN email_address operator_address
        ON operator_address.mail_account_id = operator_account.mail_account_id
       AND operator_address.address_type = 'PRIMARY'
      WHERE audit.user_id = #{userId}
      ORDER BY audit.created_at DESC, audit.id DESC
      LIMIT #{limit} OFFSET #{offset}
      """)
  List<UserAuditRecord> findUserAudits(
      @Param("userId") long userId, @Param("offset") long offset, @Param("limit") int limit);
}
