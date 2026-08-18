package com.yxoct.mail.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class EmailRestoreRepositoryTest {

  private static final String ACCOUNT_ID = "repository-test-account";
  private static final String EMAIL_ID = "repository-test-email";

  @Autowired private EmailRestoreRepository repository;

  @AfterEach
  void cleanUp() {
    repository.deleteAll(ACCOUNT_ID, List.of(EMAIL_ID));
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
}
