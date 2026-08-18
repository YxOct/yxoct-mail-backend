package com.yxoct.mail.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("user_mail_account")
public class UserMailAccountEntity {

  private Long userId;
  private Long mailAccountId;
  private MailAccountRole accountRole;
  private LocalDateTime createdAt;
}
