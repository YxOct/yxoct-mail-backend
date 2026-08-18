package com.yxoct.mail.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yxoct.mail.persistence.entity.RegistrationInvitationEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RegistrationInvitationMapper extends BaseMapper<RegistrationInvitationEntity> {

  @Select(
      """
      SELECT id, token_hash, status, purpose,
             expires_at, used_by_user_id, used_at, created_at
      FROM registration_invitation
      WHERE token_hash = #{tokenHash}
      FOR UPDATE
      """)
  RegistrationInvitationEntity findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  @Update(
      """
      UPDATE registration_invitation
      SET status = 'USED', used_by_user_id = #{userId}, used_at = #{usedAt}
      WHERE id = #{id} AND status = 'PENDING'
      """)
  int markUsed(
      @Param("id") long id, @Param("userId") long userId, @Param("usedAt") LocalDateTime usedAt);

  @Update(
      """
      UPDATE registration_invitation
      SET status = 'REVOKED'
      WHERE id = #{id} AND status = 'PENDING'
      """)
  int revoke(@Param("id") long id);
}
