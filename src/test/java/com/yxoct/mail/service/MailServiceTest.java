package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yxoct.mail.client.stalwart.JmapClient;
import com.yxoct.mail.client.stalwart.dto.EmailAddress;
import com.yxoct.mail.client.stalwart.dto.EmailBodyPart;
import com.yxoct.mail.client.stalwart.dto.EmailBodyValue;
import com.yxoct.mail.client.stalwart.dto.EmailDetailResult;
import com.yxoct.mail.client.stalwart.dto.EmailListResult;
import com.yxoct.mail.client.stalwart.dto.EmailQueryResult;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.client.stalwart.dto.MailboxGetResult;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.mail.MailDetail;
import com.yxoct.mail.domain.mail.MailPage;
import com.yxoct.mail.domain.mail.MailSummary;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

  @Mock private JmapClient jmapClient;

  private MailService mailService;
  private JmapSession session;

  @BeforeEach
  void setUp() {
    mailService = new MailService(jmapClient);
    session =
        new JmapSession(
            Map.of(),
            Map.of("urn:ietf:params:jmap:mail", "account-1"),
            "user",
            URI.create("http://localhost/jmap"),
            null,
            null,
            null,
            "state");
    lenient().when(jmapClient.getSession()).thenReturn(session);
  }

  @Test
  void returnsEmptyPageWithoutFetchingSummaries() {
    when(jmapClient.queryEmails(session, "inbox", 0, 20))
        .thenReturn(new EmailQueryResult("account-1", "query-state", 0, 0, List.of()));

    MailPage<MailSummary> page = mailService.queryEmails("inbox", 1, 20);

    assertThat(page.total()).isZero();
    assertThat(page.items()).isEmpty();
    verify(jmapClient, never()).getEmailSummaries(session, List.of());
  }

  @Test
  void rejectsPaginationPositionAboveIntegerRange() {
    assertBusinessError(
        () -> mailService.queryEmails("inbox", Integer.MAX_VALUE, 100), ErrorCode.BAD_REQUEST);

    verifyNoInteractions(jmapClient);
  }

  @Test
  void mapsEmailSummariesAndDefaultsMissingTotal() {
    when(jmapClient.queryEmails(session, "inbox", 20, 20))
        .thenReturn(new EmailQueryResult("account-1", "query-state", 20, null, List.of("email-1")));
    when(jmapClient.getEmailSummaries(session, List.of("email-1")))
        .thenReturn(
            new EmailListResult(
                "account-1",
                "state",
                List.of(
                    new EmailListResult.EmailInfo(
                        "email-1", "Subject", "Preview", "2026-08-18T00:00:00Z")),
                List.of()));

    MailPage<MailSummary> page = mailService.queryEmails("inbox", 2, 20);

    assertThat(page.total()).isZero();
    assertThat(page.items())
        .containsExactly(new MailSummary("email-1", "Subject", "Preview", "2026-08-18T00:00:00Z"));
  }

  @Test
  void rejectsEmailQueryWithoutIds() {
    when(jmapClient.queryEmails(session, "inbox", 0, 20))
        .thenReturn(new EmailQueryResult("account-1", "query-state", 0, 0, null));

    assertBusinessError(
        () -> mailService.queryEmails("inbox", 1, 20), ErrorCode.MAIL_SERVICE_UNAVAILABLE);
  }

  @Test
  void usesNotFoundResultForMissingEmail() {
    when(jmapClient.getEmailDetails(session, List.of("missing")))
        .thenReturn(new EmailDetailResult("account-1", "state", List.of(), List.of("missing")));

    assertBusinessError(() -> mailService.getEmailDetail("missing"), ErrorCode.EMAIL_NOT_FOUND);
  }

  @Test
  void fallsBackToHtmlBodyWhenTextPartHasNoPartId() {
    EmailDetailResult.EmailInfo email =
        new EmailDetailResult.EmailInfo(
            "email-1",
            "Subject",
            "Preview",
            "2026-08-18T00:00:00Z",
            List.of(new EmailAddress("Sender", "sender@example.com")),
            null,
            Map.of("html-part", new EmailBodyValue("<p>Hello</p>")),
            List.of(new EmailBodyPart(null)),
            List.of(new EmailBodyPart("html-part")));
    when(jmapClient.getEmailDetails(session, List.of("email-1")))
        .thenReturn(new EmailDetailResult("account-1", "state", List.of(email), List.of()));

    MailDetail detail = mailService.getEmailDetail("email-1");

    assertThat(detail.body()).isEqualTo("<p>Hello</p>");
    assertThat(detail.from()).hasSize(1);
    assertThat(detail.to()).isEmpty();
  }

  @Test
  void returnsNullWhenEmailHasNoBody() {
    EmailDetailResult.EmailInfo email =
        new EmailDetailResult.EmailInfo(
            "email-1", "Subject", "Preview", "2026-08-18T00:00:00Z", null, null, null, null, null);
    when(jmapClient.getEmailDetails(session, List.of("email-1")))
        .thenReturn(new EmailDetailResult("account-1", "state", List.of(email), List.of()));

    MailDetail detail = mailService.getEmailDetail("email-1");

    assertThat(detail.body()).isNull();
    assertThat(detail.from()).isEmpty();
    assertThat(detail.to()).isEmpty();
  }

  @Test
  void rejectsMailboxResultWithoutList() {
    when(jmapClient.getMailboxes(session))
        .thenReturn(new MailboxGetResult("account-1", "state", null, List.of()));

    assertBusinessError(mailService::getMailboxes, ErrorCode.MAIL_SERVICE_UNAVAILABLE);
  }

  private void assertBusinessError(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(errorCode);
  }
}
