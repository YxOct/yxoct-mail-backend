package com.yxoct.mail.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("refresh_token_session")
public class RefreshTokenSessionEntity {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;
  private String tokenHash;
  private LocalDateTime expiresAt;
  private LocalDateTime revokedAt;
  private LocalDateTime createdAt;
}
