package com.yxoct.mail.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yxoct.mail.client.stalwart.CurrentStalwartCredentialsProvider;
import com.yxoct.mail.client.stalwart.StalwartCredentials;
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
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;
import com.yxoct.mail.persistence.entity.UserStatusAuditAction;
import com.yxoct.mail.persistence.entity.UserStatusAuditEntity;
import com.yxoct.mail.persistence.mapper.AppUserMapper;
import com.yxoct.mail.persistence.mapper.EmailAddressMapper;
import com.yxoct.mail.persistence.mapper.MailAccountMapper;
import com.yxoct.mail.persistence.mapper.UserMailAccountMapper;
import com.yxoct.mail.persistence.mapper.UserStatusAuditMapper;
import com.yxoct.mail.service.MailCredentialCipher;
import com.yxoct.mail.service.RegistrationInvitationService;
import com.yxoct.mail.service.RegistrationService;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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
  @Autowired private UserStatusAuditMapper userStatusAuditMapper;
  @Autowired private RegistrationInvitationService invitationService;
  @Autowired private RegistrationService registrationService;
  @Autowired private CurrentUserRepository currentUserRepository;
  @Autowired private CurrentStalwartCredentialsProvider credentialsProvider;
  @Autowired private MailCredentialCipher credentialCipher;
  @Autowired private MailAccountSettingsRepository settingsRepository;
  @Autowired private UserStatusManagementRepository statusManagementRepository;

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
    jdbcTemplate.update("DELETE FROM registration_invitation");
    jdbcTemplate.update("DELETE FROM user_status_audit");
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

    assertThat(queryForInt("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '10'"))
        .isEqualTo(1);
    assertThat(
            queryForInt(
                "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_name IN "
                    + "('app_user', 'mail_account', 'email_address', 'user_mail_account', "
                    + "'registration_invitation', 'user_status_audit')"))
        .isEqualTo(6);
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
    assertThat(
            queryForInt(
                "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_name = 'mail_account' "
                    + "AND column_name = 'display_name' "
                    + "AND is_nullable = 'NO'"))
        .isEqualTo(1);
    assertThat(
            queryForInt(
                "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_name = 'app_user' "
                    + "AND column_name IN ('disabled_at', 'disabled_by_user_id', "
                    + "'disabled_reason')"))
        .isEqualTo(3);
    assertThat(
            queryForInt(
                "SELECT COUNT(*) FROM information_schema.statistics "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_name = 'user_status_audit' "
                    + "AND index_name = 'idx_user_status_audit_user_created'"))
        .isEqualTo(3);
  }

  @Test
  void storesCurrentDisableStateAndUserStatusAuditHistory() {
    AppUserEntity operator = insertUser();
    AppUserEntity user = insertUser();
    LocalDateTime disabledAt = LocalDateTime.of(2026, 8, 19, 20, 0);

    user.setStatus(UserStatus.DISABLED);
    user.setDisabledAt(disabledAt);
    user.setDisabledByUserId(operator.getId());
    user.setDisabledReason("Terms violation");
    assertThat(appUserMapper.updateById(user)).isEqualTo(1);

    UserStatusAuditEntity audit = new UserStatusAuditEntity();
    audit.setUserId(user.getId());
    audit.setAction(UserStatusAuditAction.DISABLED);
    audit.setReason("Terms violation");
    audit.setOperatedByUserId(operator.getId());
    audit.setCreatedAt(disabledAt);
    assertThat(userStatusAuditMapper.insert(audit)).isEqualTo(1);

    AppUserEntity storedUser = appUserMapper.selectById(user.getId());
    UserStatusAuditEntity storedAudit = userStatusAuditMapper.selectById(audit.getId());
    assertThat(storedUser.getStatus()).isEqualTo(UserStatus.DISABLED);
    assertThat(storedUser.getDisabledAt()).isEqualTo(disabledAt);
    assertThat(storedUser.getDisabledByUserId()).isEqualTo(operator.getId());
    assertThat(storedUser.getDisabledReason()).isEqualTo("Terms violation");
    assertThat(storedAudit.getAction()).isEqualTo(UserStatusAuditAction.DISABLED);
    assertThat(storedAudit.getReason()).isEqualTo("Terms violation");
    assertThat(storedAudit.getOperatedByUserId()).isEqualTo(operator.getId());
  }

  @Test
  void disablesOwnedMailAccountsAndRevokesRefreshTokens() throws Exception {
    AppUserEntity operator = insertUser();
    AppUserEntity user = insertUser();
    MailAccountEntity account = insertAccount("stalwart-account-1");
    MailAccountEntity pendingAccount = insertAccount(null);
    insertOwnership(user.getId(), account.getId());
    insertOwnership(user.getId(), pendingAccount.getId());
    jdbcTemplate.update(
        "INSERT INTO refresh_token_session "
            + "(user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?)",
        user.getId(),
        "a".repeat(64),
        LocalDateTime.of(2026, 8, 20, 20, 0),
        LocalDateTime.of(2026, 8, 19, 20, 0));
    LocalDateTime disabledAt = LocalDateTime.of(2026, 8, 19, 21, 0);

    assertThat(statusManagementRepository.findUserForUpdate(user.getId())).isPresent();
    assertThat(statusManagementRepository.findOwnedMailAccountsForUpdate(user.getId()))
        .extracting(UserStatusMailAccount::mailAccountId)
        .containsExactly(account.getId(), pendingAccount.getId());
    assertThat(
            statusManagementRepository.disableUser(
                user.getId(), operator.getId(), "Policy violation", disabledAt))
        .isTrue();
    statusManagementRepository.disableOwnedMailAccounts(user.getId(), disabledAt);
    statusManagementRepository.revokeRefreshTokens(user.getId(), disabledAt);

    assertThat(appUserMapper.selectById(user.getId()).getStatus()).isEqualTo(UserStatus.DISABLED);
    assertThat(mailAccountMapper.selectById(account.getId()).getStatus())
        .isEqualTo(MailAccountStatus.DISABLED);
    assertThat(
            queryForInt(
                "SELECT COUNT(*) FROM refresh_token_session WHERE user_id = "
                    + user.getId()
                    + " AND revoked_at IS NOT NULL"))
        .isEqualTo(1);

    LocalDateTime enabledAt = LocalDateTime.of(2026, 8, 19, 22, 0);
    assertThat(statusManagementRepository.enableUser(user.getId(), enabledAt)).isTrue();
    statusManagementRepository.enableOwnedMailAccounts(user.getId(), enabledAt);

    AppUserEntity enabledUser = appUserMapper.selectById(user.getId());
    assertThat(enabledUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(enabledUser.getDisabledAt()).isNull();
    assertThat(enabledUser.getDisabledByUserId()).isNull();
    assertThat(enabledUser.getDisabledReason()).isNull();
    assertThat(mailAccountMapper.selectById(account.getId()).getStatus())
        .isEqualTo(MailAccountStatus.ACTIVE);
    assertThat(mailAccountMapper.selectById(pendingAccount.getId()).getStatus())
        .isEqualTo(MailAccountStatus.PROVISIONING);
    assertThat(
            queryForInt(
                "SELECT COUNT(*) FROM refresh_token_session WHERE user_id = "
                    + user.getId()
                    + " AND revoked_at IS NOT NULL"))
        .isEqualTo(1);
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
    assertThat(userMailAccountMapper.countByUserId(user.getId())).isEqualTo(2);
    assertThat(
            emailAddressMapper.selectCount(
                Wrappers.<EmailAddressEntity>lambdaQuery()
                    .eq(EmailAddressEntity::getMailAccountId, firstAccount.getId())))
        .isEqualTo(2);
  }

  @Test
  void loadsTheCurrentUsersOwnedPrimaryMailAccount() {
    AppUserEntity user = insertUser();
    MailAccountEntity account = insertAccount("stalwart-account-1");
    account.setDisplayName("Alice Zhang");
    assertThat(mailAccountMapper.updateById(account)).isEqualTo(1);
    insertOwnership(user.getId(), account.getId());
    insertAddress(account.getId(), "alice@yxoct.com", "alice@yxoct.com", EmailAddressType.PRIMARY);

    assertThat(currentUserRepository.findOwnedPrimaryAccount(user.getId()))
        .contains(
            new CurrentUserAccount(
                user.getId(),
                account.getId(),
                "alice@yxoct.com",
                "Alice Zhang",
                UserRole.USER,
                UserStatus.ACTIVE,
                MailAccountStatus.ACTIVE));
  }

  @Test
  void isolatesMailCredentialsBetweenAuthenticatedUsers() {
    AppUserEntity alice = insertUser();
    AppUserEntity bob = insertUser();
    MailAccountEntity aliceAccount = insertAccount("stalwart-alice");
    MailAccountEntity bobAccount = insertAccount("stalwart-bob");
    setCredential(aliceAccount, "alice-mail-secret");
    setCredential(bobAccount, "bob-mail-secret");
    insertOwnership(alice.getId(), aliceAccount.getId());
    insertOwnership(bob.getId(), bobAccount.getId());
    insertAddress(
        aliceAccount.getId(), "alice@yxoct.com", "alice@yxoct.com", EmailAddressType.PRIMARY);
    insertAddress(bobAccount.getId(), "bob@yxoct.com", "bob@yxoct.com", EmailAddressType.PRIMARY);

    authenticateAs(alice.getId());
    assertThat(credentialsProvider.getCredentials())
        .isEqualTo(
            new StalwartCredentials(
                "user:" + alice.getId() + ":account:" + aliceAccount.getId(),
                "alice@yxoct.com",
                "alice-mail-secret"));

    authenticateAs(bob.getId());
    assertThat(credentialsProvider.getCredentials())
        .isEqualTo(
            new StalwartCredentials(
                "user:" + bob.getId() + ":account:" + bobAccount.getId(),
                "bob@yxoct.com",
                "bob-mail-secret"));
  }

  @Test
  void updatesOnlyAnOwnedMailAccountDisplayName() {
    AppUserEntity alice = insertUser();
    AppUserEntity bob = insertUser();
    MailAccountEntity account = insertAccount("stalwart-alice");
    insertOwnership(alice.getId(), account.getId());

    assertThat(settingsRepository.findOwnedForUpdate(bob.getId(), account.getId())).isEmpty();
    assertThat(settingsRepository.findOwnedForUpdate(alice.getId(), account.getId()))
        .contains(
            new OwnedMailAccount(
                account.getId(), "stalwart-alice", "Test Account", MailAccountStatus.ACTIVE));

    LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 19, 19, 0);
    assertThat(settingsRepository.updateDisplayName(account.getId(), "Alice Zhang", updatedAt))
        .isTrue();
    assertThat(mailAccountMapper.selectById(account.getId()).getDisplayName())
        .isEqualTo("Alice Zhang");
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
          new RegisterRequest(invitationToken, localPart, null, "correct horse battery staple"));
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
    account.setDisplayName("Test Account");
    account.setStalwartAccountId(stalwartAccountId);
    account.setStatus(MailAccountStatus.ACTIVE);
    assertThat(mailAccountMapper.insert(account)).isEqualTo(1);
    assertThat(account.getId()).isNotNull();
    return account;
  }

  private void setCredential(MailAccountEntity account, String password) {
    account.setCredentialCiphertext(credentialCipher.encrypt(password));
    assertThat(mailAccountMapper.updateById(account)).isEqualTo(1);
  }

  private void authenticateAs(long userId) {
    Instant now = Instant.now();
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(Long.toString(userId))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(jwt, "token"));
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
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
