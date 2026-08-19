package com.yxoct.mail.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserAuthVersionMapper {

  @Select("SELECT version FROM app_user WHERE id = #{userId}")
  Long findVersion(@Param("userId") long userId);
}
