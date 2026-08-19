package com.yxoct.mail.persistence.mapper;

import com.yxoct.mail.persistence.PasswordChangeTarget;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserPasswordMapper {

  @Select(
      """
      SELECT id AS user_id, password_hash, version, must_change_password
      FROM app_user
      WHERE id = #{userId}
      FOR UPDATE
      """)
  PasswordChangeTarget findForUpdate(@Param("userId") long userId);

  @Update(
      """
      UPDATE app_user
      SET password_hash = #{passwordHash},
          version = version + 1,
          must_change_password = #{mustChangePassword},
          updated_at = #{updatedAt}
      WHERE id = #{userId} AND version = #{expectedVersion}
      """)
  int updatePassword(
      @Param("userId") long userId,
      @Param("expectedVersion") long expectedVersion,
      @Param("passwordHash") String passwordHash,
      @Param("mustChangePassword") boolean mustChangePassword,
      @Param("updatedAt") LocalDateTime updatedAt);
}
