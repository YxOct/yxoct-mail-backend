package com.yxoct.mail.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class EmailRestoreRepositoryTest {

  private static final String ACCOUNT_ID = "repository-test-account";
  private static final String EMAIL_ID = "repository-test-email";

  @Autowired private EmailRestoreRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    repository.deleteAll(ACCOUNT_ID, List.of(EMAIL_ID));
    repository.deleteAll(ACCOUNT_ID, List.of("expired-email", "current-email"));
  }

  @Test
  void savesOriginalMailboxMembershipsOnlyOnce() {
    assertThat(repository.saveIfAbsent(ACCOUNT_ID, EMAIL_ID, List.of("inbox", "archive"))).isTrue();
    assertThat(repository.saveIfAbsent(ACCOUNT_ID, EMAIL_ID, List.of("other"))).isFalse();

    assertThat(repository.findMailboxIds(ACCOUNT_ID, EMAIL_ID))
        .hasValue(List.of("archive", "inbox"));
  }

  @Test
  void deletesRecordAndMailboxMemberships() {
    repository.saveIfAbsent(ACCOUNT_ID, EMAIL_ID, List.of("inbox"));

    repository.deleteAll(ACCOUNT_ID, List.of(EMAIL_ID));

    assertThat(repository.findMailboxIds(ACCOUNT_ID, EMAIL_ID)).isEmpty();
  }

  @Test
  void deletesOnlyRestoreRecordsBeforeCutoff() {
    repository.saveIfAbsent(ACCOUNT_ID, "expired-email", List.of("inbox"));
    repository.saveIfAbsent(ACCOUNT_ID, "current-email", List.of("archive"));
    LocalDateTime cutoff = LocalDateTime.of(2026, 7, 23, 8, 0);
    jdbcTemplate.update(
        "UPDATE email_restore_record SET deleted_at = ? WHERE account_id = ? AND email_id = ?",
        cutoff.minusSeconds(1),
        ACCOUNT_ID,
        "expired-email");
    jdbcTemplate.update(
        "UPDATE email_restore_record SET deleted_at = ? WHERE account_id = ? AND email_id = ?",
        cutoff,
        ACCOUNT_ID,
        "current-email");

    assertThat(repository.deleteBefore(cutoff, 1)).isEqualTo(1);

    assertThat(repository.findMailboxIds(ACCOUNT_ID, "expired-email")).isEmpty();
    assertThat(repository.findMailboxIds(ACCOUNT_ID, "current-email")).hasValue(List.of("archive"));
  }
}
