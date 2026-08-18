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
@TableName("mail_account")
public class MailAccountEntity {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String stalwartAccountId;
  private MailAccountStatus status;
  private String credentialCiphertext;
  private Integer provisioningAttempts;
  private LocalDateTime provisioningLeaseUntil;
  private LocalDateTime nextProvisioningAt;
  private String lastProvisioningError;
  private Long version;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
