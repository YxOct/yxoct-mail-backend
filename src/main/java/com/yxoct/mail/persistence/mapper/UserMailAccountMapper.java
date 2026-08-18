package com.yxoct.mail.persistence.mapper;

import com.yxoct.mail.persistence.entity.UserMailAccountEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMailAccountMapper {

  @Insert(
      """
      INSERT INTO user_mail_account (user_id, mail_account_id, account_role)
      VALUES (#{userId}, #{mailAccountId}, #{accountRole})
      """)
  int insert(UserMailAccountEntity relationship);

  @Select("SELECT COUNT(*) FROM user_mail_account WHERE user_id = #{userId}")
  long countByUserId(@Param("userId") long userId);
}
