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
@TableName("email_address")
public class EmailAddressEntity {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long mailAccountId;
  private String address;
  private String normalizedAddress;
  private EmailAddressType addressType;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
