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
@TableName("registration_invitation")
public class RegistrationInvitationEntity {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String tokenHash;
  private RegistrationInvitationStatus status;
  private Integer mailAccountLimit;
  private Integer emailAddressLimit;
  private LocalDateTime expiresAt;
  private Long usedByUserId;
  private LocalDateTime usedAt;
  private LocalDateTime createdAt;
}
