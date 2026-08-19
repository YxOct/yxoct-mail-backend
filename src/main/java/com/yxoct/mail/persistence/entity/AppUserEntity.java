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
@TableName("app_user")
public class AppUserEntity {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String passwordHash;
  private UserStatus status;
  private UserRole role;
  private LocalDateTime disabledAt;
  private Long disabledByUserId;
  private String disabledReason;
  private Long version;
  private Boolean mustChangePassword;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
