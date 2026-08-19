package com.yxoct.mail.persistence.mapper;

import com.yxoct.mail.persistence.AdminUserRecord;
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
}
