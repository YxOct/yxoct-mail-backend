package com.yxoct.mail.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yxoct.mail.persistence.entity.RefreshTokenSessionEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RefreshTokenSessionMapper extends BaseMapper<RefreshTokenSessionEntity> {

  @Select(
      """
      SELECT id, user_id, token_hash, expires_at, revoked_at, created_at
      FROM refresh_token_session
      WHERE token_hash = #{tokenHash}
      FOR UPDATE
      """)
  RefreshTokenSessionEntity findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  @Update(
      """
      UPDATE refresh_token_session
      SET revoked_at = #{revokedAt}
      WHERE id = #{id}
        AND revoked_at IS NULL
      """)
  int revoke(@Param("id") long id, @Param("revokedAt") LocalDateTime revokedAt);

  @Update(
      """
      UPDATE refresh_token_session
      SET revoked_at = #{revokedAt}
      WHERE token_hash = #{tokenHash}
        AND revoked_at IS NULL
      """)
  int revokeByTokenHash(
      @Param("tokenHash") String tokenHash, @Param("revokedAt") LocalDateTime revokedAt);

  @Update(
      """
      UPDATE refresh_token_session
      SET revoked_at = #{revokedAt}
      WHERE user_id = #{userId}
        AND revoked_at IS NULL
      """)
  int revokeByUserId(@Param("userId") long userId, @Param("revokedAt") LocalDateTime revokedAt);
}
