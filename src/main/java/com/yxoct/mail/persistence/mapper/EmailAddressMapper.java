package com.yxoct.mail.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yxoct.mail.persistence.entity.EmailAddressEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailAddressMapper extends BaseMapper<EmailAddressEntity> {}
