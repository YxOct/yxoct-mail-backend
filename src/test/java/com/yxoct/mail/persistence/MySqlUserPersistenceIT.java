package com.yxoct.mail.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.CreatedRegistrationInvitation;
import com.yxoct.mail.domain.user.RegisterRequest;
import com.yxoct.mail.domain.user.RegistrationResult;
import com.yxoct.mail.persistence.entity.AppUserEntity;
import com.yxoct.mail.persistence.entity.EmailAddressEntity;
import com.yxoct.mail.persistence.entity.EmailAddressType;
import com.yxoct.mail.persistence.entity.MailAccountEntity;
import com.yxoct.mail.persistence.entity.MailAccountRole;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.persistence.entity.UserMailAccountEntity;
import com.yxoct.mail.persistence.entity.UserStatus;
import com.yxoct.mail.persistence.mapper.AppUserMapper;
import com.yxoct.mail.persistence.mapper.EmailAddressMapper;
import com.yxoct.mail.persistence.mapper.MailAccountMapper;
import com.yxoct.mail.persistence.mapper.UserMailAccountMapper;
import com.yxoct.mail.service.RegistrationInvitationService;
import com.yxoct.mail.service.RegistrationService;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@ActiveProfiles("mysql-it")
@SpringBootTest
class MySqlUserPersistenceIT {

  @SuppressWarnings("resource") // Lifecycle is managed by the Testcontainers extension.
  @Container
  @ServiceConnection
  static final MySQLContainer MYSQL =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("yxoct_mail")
          .withUsername("yxoct_mail")
          .withPassword("test-password");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AppUserMapper appUserMapper;
  @Autowired private MailAccountMapper mailAccountMapper;
  @Autowired private EmailAddressMapper emailAddressMapper;
  @Autowired private UserMailAccountMapper userMailAccountMapper;
  @Autowired private RegistrationInvitationService invitationService;
  @Autowired private RegistrationService registrationService;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM registration_invitation");
    jdbcTemplate.update("DELETE FROM user_mail_account");
    jdbcTemplate.update("DELETE FROM email_address");
    jdbcTemplate.update("DELETE FROM mail_account");
    jdbcTemplate.update("DELETE FROM app_user");
  }

  @Test
  void createsUserAndMailAccountSchemaOnMySql() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
    }

    assertThat(queryForInt("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5'"))
        .isEqualTo(1);
    assertThat(
            queryForInt(
                "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_name IN "
                    + "('app_user', 'mail_account', 'email_address', 'user_mail_account', "
                    + "'registration_invitation')"))
        .isEqualTo(5);
    assertThat(
            queryForInt(
                "SELECT COUNT(*) FROM information_schema.statistics "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_name = 'email_address' "
                    + "AND index_name = 'uk_email_address_normalized_address' "
                    + "AND non_unique = 0"))
        .isEqualTo(1);
    assertThat(
            queryForInt(
                "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() "
                    + "AND column_name IN ('mail_account_limit', 'email_address_limit')"))
        .isZero();
    assertThat(
            queryForInt(
                "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_name = 'mail_account' "
                    + "AND column_name IN ('credential_ciphertext', 'provisioning_attempts', "
                    + "'provisioning_lease_until', 'next_provisioning_at', "
                    + "'last_provisioning_error')"))
        .isEqualTo(5);
  }

  @Test
  void supportsMultipleAccountsAndAddressesWithoutHardCodedLimits() {
    AppUserEntity user = insertUser();
    MailAccountEntity firstAccount = insertAccount("stalwart-account-1");
    MailAccountEntity secondAccount = insertAccount("stalwart-account-2");

    insertOwnership(user.getId(), firstAccount.getId());
    insertOwnership(user.getId(), secondAccount.getId());
    insertAddress(
        firstAccount.getId(), "alice@yxoct.com", "alice@yxoct.com", EmailAddressType.PRIMARY);
    insertAddress(
        firstAccount.getId(), "alias@yxoct.com", "alias@yxoct.com", EmailAddressType.ALIAS);

    assertThat(appUserMapper.selectById(user.getId()).getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(mailAccountMapper.selectById(firstAccount.getId()).getStatus())
        .isEqualTo(MailAccountStatus.ACTIVE);
    assertThat(
            userMailAccountMapper.selectCount(
                Wrappers.<UserMailAccountEntity>lambdaQuery()
                    .eq(UserMailAccountEntity::getUserId, user.getId())))
        .isEqualTo(2);
    assertThat(
            emailAddressMapper.selectCount(
                Wrappers.<EmailAddressEntity>lambdaQuery()
                    .eq(EmailAddressEntity::getMailAccountId, firstAccount.getId())))
        .isEqualTo(2);
  }

  @Test
  void rejectsDuplicateNormalizedEmailAddresses() {
    AppUserEntity user = insertUser();
    MailAccountEntity firstAccount = insertAccount("stalwart-account-1");
    MailAccountEntity secondAccount = insertAccount("stalwart-account-2");
    insertOwnership(user.getId(), firstAccount.getId());
    insertOwnership(user.getId(), secondAccount.getId());
    insertAddress(
        firstAccount.getId(), "alice@yxoct.com", "alice@yxoct.com", EmailAddressType.PRIMARY);

    assertThatThrownBy(
            () ->
                insertAddress(
                    secondAccount.getId(),
                    "Alice@YXOct.com",
                    "alice@yxoct.com",
                    EmailAddressType.PRIMARY))
        .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void consumesAnInvitationOnlyOnceUnderConcurrentRegistration() throws Exception {
    CreatedRegistrationInvitation invitation = invitationService.create();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    List<Object> outcomes;
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      List<Future<Object>> futures =
          List.of(
              executor.submit(() -> registerAfterSignal(invitation.token(), "alice", ready, start)),
              executor.submit(() -> registerAfterSignal(invitation.token(), "bob", ready, start)));
      ready.await();
      start.countDown();
      outcomes = List.of(futures.get(0).get(), futures.get(1).get());
    }

    assertThat(outcomes).filteredOn(RegistrationResult.class::isInstance).hasSize(1);
    assertThat(outcomes).filteredOn(ErrorCode.INVITATION_ALREADY_USED::equals).hasSize(1);
    assertThat(appUserMapper.selectCount(null)).isEqualTo(1);
  }

  private Object registerAfterSignal(
      String invitationToken, String localPart, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await();
    try {
      return registrationService.register(
          new RegisterRequest(invitationToken, localPart, "correct horse battery staple"));
    } catch (BusinessException exception) {
      return exception.getErrorCode();
    }
  }

  private AppUserEntity insertUser() {
    AppUserEntity user = new AppUserEntity();
    user.setPasswordHash("test-password-hash");
    user.setStatus(UserStatus.ACTIVE);
    assertThat(appUserMapper.insert(user)).isEqualTo(1);
    assertThat(user.getId()).isNotNull();
    return user;
  }

  private MailAccountEntity insertAccount(String stalwartAccountId) {
    MailAccountEntity account = new MailAccountEntity();
    account.setStalwartAccountId(stalwartAccountId);
    account.setStatus(MailAccountStatus.ACTIVE);
    assertThat(mailAccountMapper.insert(account)).isEqualTo(1);
    assertThat(account.getId()).isNotNull();
    return account;
  }

  private void insertOwnership(Long userId, Long mailAccountId) {
    UserMailAccountEntity ownership = new UserMailAccountEntity();
    ownership.setUserId(userId);
    ownership.setMailAccountId(mailAccountId);
    ownership.setAccountRole(MailAccountRole.OWNER);
    assertThat(userMailAccountMapper.insert(ownership)).isEqualTo(1);
  }

  private void insertAddress(
      Long mailAccountId, String address, String normalizedAddress, EmailAddressType addressType) {
    EmailAddressEntity emailAddress = new EmailAddressEntity();
    emailAddress.setMailAccountId(mailAccountId);
    emailAddress.setAddress(address);
    emailAddress.setNormalizedAddress(normalizedAddress);
    emailAddress.setAddressType(addressType);
    assertThat(emailAddressMapper.insert(emailAddress)).isEqualTo(1);
  }

  private int queryForInt(String sql) throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      assertThat(resultSet.next()).isTrue();
      return resultSet.getInt(1);
    }
  }
}
