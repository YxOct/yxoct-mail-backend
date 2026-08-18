package com.yxoct.mail.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@ActiveProfiles("mysql-it")
@SpringBootTest
class MySqlEmailRestoreRepositoryIT {

  private static final String ACCOUNT_ID = "mysql-it-account";
  private static final List<String> EMAIL_IDS =
      List.of("mysql-it-email", "mysql-it-rollback-email");

  @SuppressWarnings("resource") // Lifecycle is managed by the Testcontainers extension.
  @Container
  @ServiceConnection
  static final MySQLContainer MYSQL =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("yxoct_mail")
          .withUsername("yxoct_mail")
          .withPassword("test-password");

  @Autowired private DataSource dataSource;

  @Autowired private EmailRestoreRepository repository;

  @AfterEach
  void cleanUp() {
    repository.deleteAll(ACCOUNT_ID, EMAIL_IDS);
  }

  @Test
  void runsFlywayMigrationAgainstMySql() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
    }

    assertThat(queryForInt("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1'"))
        .isEqualTo(1);
    assertThat(
            queryForInt(
                "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_name IN ('email_restore_record', 'email_restore_mailbox')"))
        .isEqualTo(2);
    assertThat(
            queryForInt(
                "SELECT COUNT(*) FROM information_schema.referential_constraints "
                    + "WHERE constraint_schema = DATABASE() "
                    + "AND constraint_name = 'fk_email_restore_mailbox_record' "
                    + "AND delete_rule = 'CASCADE'"))
        .isEqualTo(1);
  }

  @Test
  void persistsAndDeletesRestoreLocationsOnMySql() {
    String emailId = EMAIL_IDS.getFirst();

    assertThat(repository.saveIfAbsent(ACCOUNT_ID, emailId, List.of("inbox", "archive"))).isTrue();
    assertThat(repository.saveIfAbsent(ACCOUNT_ID, emailId, List.of("other"))).isFalse();
    assertThat(repository.findMailboxIds(ACCOUNT_ID, emailId))
        .hasValue(List.of("archive", "inbox"));

    repository.deleteAll(ACCOUNT_ID, List.of(emailId));

    assertThat(repository.findMailboxIds(ACCOUNT_ID, emailId)).isEmpty();
  }

  @Test
  void rollsBackRecordWhenMailboxInsertFails() {
    String emailId = EMAIL_IDS.get(1);
    String oversizedMailboxId = "m".repeat(256);

    assertThatThrownBy(
            () -> repository.saveIfAbsent(ACCOUNT_ID, emailId, List.of(oversizedMailboxId)))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(repository.findMailboxIds(ACCOUNT_ID, emailId)).isEmpty();
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
