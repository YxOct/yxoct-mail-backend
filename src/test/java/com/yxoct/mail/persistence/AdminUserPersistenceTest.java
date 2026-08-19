package com.yxoct.mail.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class AdminUserPersistenceTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AdminUserRepository repository;

  @Test
  void queriesUsersWithTheirPrimaryOwnedMailAccount() {
    jdbcTemplate.update(
        "INSERT INTO app_user (id, password_hash, status, role) VALUES (7, 'hash', 'ACTIVE', 'USER')");
    jdbcTemplate.update(
        "INSERT INTO mail_account (id, display_name, status) VALUES (9, 'Alice', 'ACTIVE')");
    jdbcTemplate.update(
        "INSERT INTO user_mail_account (user_id, mail_account_id, account_role) VALUES (7, 9, 'OWNER')");
    jdbcTemplate.update(
        "INSERT INTO email_address (id, mail_account_id, address, normalized_address, address_type) "
            + "VALUES (11, 9, 'alice@yxoct.com', 'alice@yxoct.com', 'PRIMARY')");

    AdminUserRecord user = repository.findById(7).orElseThrow();

    assertThat(user.primaryEmailAddress()).isEqualTo("alice@yxoct.com");
    assertThat(user.displayName()).isEqualTo("Alice");
    assertThat(user.role()).isEqualTo(UserRole.USER);
    assertThat(user.userStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.mailAccountId()).isEqualTo(9);
    assertThat(user.mailAccountStatus()).isEqualTo(MailAccountStatus.ACTIVE);
    assertThat(repository.findPage(1, 20)).extracting(AdminUserRecord::userId).contains(7L);
  }
}
