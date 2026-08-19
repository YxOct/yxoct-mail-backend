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
@TableName("user_status_audit")
public class UserStatusAuditEntity {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;
  private UserStatusAuditAction action;
  private String reason;
  private Long operatedByUserId;
  private LocalDateTime createdAt;
}
