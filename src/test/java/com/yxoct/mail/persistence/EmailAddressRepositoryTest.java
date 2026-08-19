package com.yxoct.mail.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yxoct.mail.persistence.entity.EmailAddressEntity;
import com.yxoct.mail.persistence.entity.EmailAddressType;
import com.yxoct.mail.persistence.mapper.EmailAddressMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailAddressRepositoryTest {

  @Mock private EmailAddressMapper mapper;

  @Test
  void returnsThePrimaryAddressFirstAndAliasesById() {
    when(mapper.selectList(any()))
        .thenReturn(
            List.of(
                address(13, EmailAddressType.ALIAS),
                address(10, EmailAddressType.PRIMARY),
                address(12, EmailAddressType.ALIAS)));

    assertThat(new EmailAddressRepository(mapper).findAllByMailAccountId(2))
        .extracting(EmailAddressEntity::getId)
        .containsExactly(10L, 12L, 13L);
  }

  private EmailAddressEntity address(long id, EmailAddressType type) {
    EmailAddressEntity address = new EmailAddressEntity();
    address.setId(id);
    address.setMailAccountId(2L);
    address.setAddressType(type);
    return address;
  }
}
