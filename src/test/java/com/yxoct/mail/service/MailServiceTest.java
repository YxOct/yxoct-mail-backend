package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yxoct.mail.client.stalwart.JmapClient;
import com.yxoct.mail.client.stalwart.JmapSessionCache;
import com.yxoct.mail.client.stalwart.dto.EmailAddress;
import com.yxoct.mail.client.stalwart.dto.EmailAttachmentResult;
import com.yxoct.mail.client.stalwart.dto.EmailBodyPart;
import com.yxoct.mail.client.stalwart.dto.EmailBodyValue;
import com.yxoct.mail.client.stalwart.dto.EmailDetailResult;
import com.yxoct.mail.client.stalwart.dto.EmailListResult;
import com.yxoct.mail.client.stalwart.dto.EmailQueryResult;
import com.yxoct.mail.client.stalwart.dto.EmailUpdateResult;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.client.stalwart.dto.MailboxGetResult;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.mail.MailAddress;
import com.yxoct.mail.domain.mail.MailAttachment;
import com.yxoct.mail.domain.mail.MailBatchUpdateResult;
import com.yxoct.mail.domain.mail.MailDetail;
import com.yxoct.mail.domain.mail.MailPage;
import com.yxoct.mail.domain.mail.MailQueryFilter;
import com.yxoct.mail.domain.mail.MailSort;
import com.yxoct.mail.domain.mail.MailSummary;
import com.yxoct.mail.domain.mail.MailboxRole;
import java.io.ByteArrayOutputStream;
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
  @Mock private JmapSessionCache sessionCache;

  private MailService mailService;
  private JmapSession session;

  @BeforeEach
  void setUp() {
    mailService = new MailService(jmapClient, sessionCache, new EmailHtmlSanitizer());
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
    lenient().when(sessionCache.getSession()).thenReturn(session);
  }

  @Test
  void returnsEmptyPageWithoutFetchingSummaries() {
    when(jmapClient.queryEmails(
            session, "inbox", 0, 20, MailQueryFilter.none(), MailSort.defaultSort()))
        .thenReturn(new EmailQueryResult("account-1", "query-state", 0, 0, List.of()));

    MailPage<MailSummary> page =
        mailService.queryEmails("inbox", 1, 20, MailQueryFilter.none(), MailSort.defaultSort());

    assertThat(page.total()).isZero();
    assertThat(page.items()).isEmpty();
    verify(jmapClient, never()).getEmailSummaries(session, List.of());
  }

  @Test
  void rejectsPaginationPositionAboveIntegerRange() {
    assertBusinessError(
        () ->
            mailService.queryEmails(
                "inbox", Integer.MAX_VALUE, 100, MailQueryFilter.none(), MailSort.defaultSort()),
        ErrorCode.BAD_REQUEST);

    verifyNoInteractions(jmapClient, sessionCache);
  }

  @Test
  void rejectsMissingQueryFilter() {
    assertBusinessError(
        () -> mailService.queryEmails("inbox", 1, 20, null, MailSort.defaultSort()),
        ErrorCode.BAD_REQUEST);

    verifyNoInteractions(jmapClient, sessionCache);
  }

  @Test
  void rejectsMissingQuerySort() {
    assertBusinessError(
        () -> mailService.queryEmails("inbox", 1, 20, MailQueryFilter.none(), null),
        ErrorCode.BAD_REQUEST);

    verifyNoInteractions(jmapClient, sessionCache);
  }

  @Test
  void mapsEmailSummariesAndDefaultsMissingTotal() {
    when(jmapClient.queryEmails(
            session, "inbox", 20, 20, MailQueryFilter.none(), MailSort.defaultSort()))
        .thenReturn(new EmailQueryResult("account-1", "query-state", 20, null, List.of("email-1")));
    when(jmapClient.getEmailSummaries(session, List.of("email-1")))
        .thenReturn(
            new EmailListResult(
                "account-1",
                "state",
                List.of(
                    new EmailListResult.EmailInfo(
                        "email-1",
                        Map.of("archive", false, "inbox", true),
                        "Subject",
                        "Preview",
                        List.of(new EmailAddress("Sender", "sender@example.com")),
                        List.of(new EmailAddress("Recipient", "recipient@example.com")),
                        "2026-08-18T00:00:00Z",
                        "2026-08-17T23:59:00Z",
                        true,
                        4096L,
                        Map.of("$seen", true, "$flagged", true))),
                List.of()));

    MailPage<MailSummary> page =
        mailService.queryEmails("inbox", 2, 20, MailQueryFilter.none(), MailSort.defaultSort());

    assertThat(page.total()).isZero();
    assertThat(page.items())
        .containsExactly(
            new MailSummary(
                "email-1",
                List.of("inbox"),
                "Subject",
                "Preview",
                List.of(new MailAddress("Sender", "sender@example.com")),
                List.of(new MailAddress("Recipient", "recipient@example.com")),
                "2026-08-18T00:00:00Z",
                "2026-08-17T23:59:00Z",
                true,
                true,
                true,
                4096L));
  }

  @Test
  void mapsMailboxCountsAndStableRoles() {
    when(jmapClient.getMailboxes(session))
        .thenReturn(
            new MailboxGetResult(
                "account-1",
                "state",
                List.of(
                    new MailboxGetResult.MailboxInfo("inbox", "收件箱", "inbox", 3L, 10L, 1L),
                    new MailboxGetResult.MailboxInfo("archive", "归档", null, 0L, 5L, 2L)),
                List.of()));

    assertThat(mailService.getMailboxes())
        .extracting("id", "role", "unreadCount", "totalCount", "sortOrder")
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("inbox", MailboxRole.INBOX, 3L, 10L, 1L),
            org.assertj.core.groups.Tuple.tuple("archive", MailboxRole.OTHER, 0L, 5L, 2L));
  }

  @Test
  void rejectsEmailQueryWithoutIds() {
    when(jmapClient.queryEmails(
            session, "inbox", 0, 20, MailQueryFilter.none(), MailSort.defaultSort()))
        .thenReturn(new EmailQueryResult("account-1", "query-state", 0, 0, null));

    assertBusinessError(
        () ->
            mailService.queryEmails("inbox", 1, 20, MailQueryFilter.none(), MailSort.defaultSort()),
        ErrorCode.MAIL_SERVICE_UNAVAILABLE);
  }

  @Test
  void usesNotFoundResultForMissingEmail() {
    when(jmapClient.getEmailDetails(session, List.of("missing")))
        .thenReturn(new EmailDetailResult("account-1", "state", List.of(), List.of("missing")));

    assertBusinessError(() -> mailService.getEmailDetail("missing"), ErrorCode.EMAIL_NOT_FOUND);
  }

  @Test
  void mapsEmailDetailEnvelopeFields() {
    when(jmapClient.getEmailDetails(session, List.of("email-1")))
        .thenReturn(
            new EmailDetailResult(
                "account-1",
                "state",
                List.of(
                    new EmailDetailResult.EmailInfo(
                        "email-1",
                        Map.of("archive", false, "inbox", true),
                        "Subject",
                        "Preview",
                        "2026-08-18T00:00:00Z",
                        "2026-08-17T23:59:00Z",
                        List.of(new EmailAddress("Sender", "sender@example.com")),
                        List.of(new EmailAddress("Recipient", "recipient@example.com")),
                        List.of(new EmailAddress("Copy", "copy@example.com")),
                        List.of(new EmailAddress("Hidden", "hidden@example.com")),
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of())),
                List.of()));

    MailDetail detail = mailService.getEmailDetail("email-1");

    assertThat(detail.mailboxIds()).containsExactly("inbox");
    assertThat(detail.sentAt()).isEqualTo("2026-08-17T23:59:00Z");
    assertThat(detail.cc()).containsExactly(new MailAddress("Copy", "copy@example.com"));
    assertThat(detail.bcc()).containsExactly(new MailAddress("Hidden", "hidden@example.com"));
  }

  @Test
  void returnsSanitizedHtmlBodyWhenPlainTextIsUnavailable() {
    EmailDetailResult.EmailInfo email =
        new EmailDetailResult.EmailInfo(
            "email-1",
            "Subject",
            "Preview",
            "2026-08-18T00:00:00Z",
            List.of(new EmailAddress("Sender", "sender@example.com")),
            null,
            Map.of(
                "html-part",
                new EmailBodyValue(
                    "<p onclick=\"alert(1)\">Hello<script>x()</script></p>"
                        + "<img src=\"cid:logo@example\"><img src=\"https://tracker.example/pixel\">")),
            List.of(),
            List.of(new EmailBodyPart("html-part", null, null, null, "text/html", null, null)),
            List.of(
                new EmailBodyPart(
                    "attachment-part",
                    "blob-1",
                    2048L,
                    "report.pdf",
                    "application/pdf",
                    "attachment",
                    "attachment@example"),
                new EmailBodyPart(
                    "image-part",
                    "blob-2",
                    1024L,
                    "logo.png",
                    "image/png",
                    "inline",
                    "logo@example")),
            Map.of("$seen", true, "$flagged", true));
    when(jmapClient.getEmailDetails(session, List.of("email-1")))
        .thenReturn(new EmailDetailResult("account-1", "state", List.of(email), List.of()));

    MailDetail detail = mailService.getEmailDetail("email-1");

    assertThat(detail.body())
        .isEqualTo("<p>Hello</p><img src=\"/api/mail/emails/email-1/attachments/blob-2\"><img>");
    assertThat(detail.textBody()).isNull();
    assertThat(detail.htmlBody()).isEqualTo(detail.body());
    assertThat(detail.from()).hasSize(1);
    assertThat(detail.to()).isEmpty();
    assertThat(detail.read()).isTrue();
    assertThat(detail.starred()).isTrue();
    assertThat(detail.attachments()).hasSize(2);
    assertThat(detail.attachments().getFirst().blobId()).isEqualTo("blob-1");
    assertThat(detail.attachments().getFirst().inline()).isFalse();
    assertThat(detail.attachments().get(1).inline()).isTrue();
    assertThat(detail.attachments().get(1).cid()).isEqualTo("logo@example");
  }

  @Test
  void exposesPlainTextAndHtmlAlternativesSeparately() {
    EmailDetailResult.EmailInfo email =
        new EmailDetailResult.EmailInfo(
            "email-1",
            "Subject",
            "Preview",
            "2026-08-18T00:00:00Z",
            null,
            null,
            Map.of(
                "text-part",
                new EmailBodyValue("Hello"),
                "html-part",
                new EmailBodyValue("<p>Hello</p>")),
            List.of(new EmailBodyPart("text-part", null, null, null, "text/plain", null, null)),
            List.of(new EmailBodyPart("html-part", null, null, null, "text/html", null, null)),
            List.of(),
            null);
    when(jmapClient.getEmailDetails(session, List.of("email-1")))
        .thenReturn(new EmailDetailResult("account-1", "state", List.of(email), List.of()));

    MailDetail detail = mailService.getEmailDetail("email-1");

    assertThat(detail.body()).isEqualTo("Hello");
    assertThat(detail.textBody()).isEqualTo("Hello");
    assertThat(detail.htmlBody()).isEqualTo("<p>Hello</p>");
  }

  @Test
  void returnsNullWhenEmailHasNoBody() {
    EmailDetailResult.EmailInfo email =
        new EmailDetailResult.EmailInfo(
            "email-1",
            "Subject",
            "Preview",
            "2026-08-18T00:00:00Z",
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null);
    when(jmapClient.getEmailDetails(session, List.of("email-1")))
        .thenReturn(new EmailDetailResult("account-1", "state", List.of(email), List.of()));

    MailDetail detail = mailService.getEmailDetail("email-1");

    assertThat(detail.body()).isNull();
    assertThat(detail.textBody()).isNull();
    assertThat(detail.htmlBody()).isNull();
    assertThat(detail.from()).isEmpty();
    assertThat(detail.to()).isEmpty();
    assertThat(detail.read()).isFalse();
    assertThat(detail.starred()).isFalse();
    assertThat(detail.attachments()).isEmpty();
  }

  @Test
  void rejectsInvalidAttachmentMetadata() {
    EmailDetailResult.EmailInfo email =
        new EmailDetailResult.EmailInfo(
            "email-1",
            "Subject",
            "Preview",
            "2026-08-18T00:00:00Z",
            null,
            null,
            null,
            null,
            null,
            List.of(new EmailBodyPart("part-1", null, 10L, "file.txt", "text/plain", null, null)),
            null);
    when(jmapClient.getEmailDetails(session, List.of("email-1")))
        .thenReturn(new EmailDetailResult("account-1", "state", List.of(email), List.of()));

    assertBusinessError(
        () -> mailService.getEmailDetail("email-1"), ErrorCode.MAIL_SERVICE_UNAVAILABLE);
  }

  @Test
  void findsAttachmentOnlyWhenItBelongsToEmail() {
    EmailDetailResult.EmailInfo email = emailWithAttachment();
    when(jmapClient.getEmailAttachments(session, List.of("email-1")))
        .thenReturn(attachmentResult(email));

    MailAttachment attachment = mailService.getAttachment("email-1", "blob-1");

    assertThat(attachment.name()).isEqualTo("report.pdf");
  }

  @Test
  void rejectsBlobThatDoesNotBelongToEmail() {
    EmailDetailResult.EmailInfo email = emailWithAttachment();
    when(jmapClient.getEmailAttachments(session, List.of("email-1")))
        .thenReturn(attachmentResult(email));

    assertBusinessError(
        () -> mailService.getAttachment("email-1", "other-blob"), ErrorCode.ATTACHMENT_NOT_FOUND);
  }

  @Test
  void reportsMissingEmailBeforeAttachmentLookup() {
    when(jmapClient.getEmailAttachments(session, List.of("missing")))
        .thenReturn(new EmailAttachmentResult("account-1", "state", List.of(), List.of("missing")));

    assertBusinessError(
        () -> mailService.getAttachment("missing", "blob-1"), ErrorCode.EMAIL_NOT_FOUND);
  }

  @Test
  void delegatesAttachmentStreamingToJmapClient() {
    MailAttachment attachment =
        new MailAttachment("part-1", "blob-1", "report.pdf", "application/pdf", 2048, false, null);
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    mailService.downloadAttachment(attachment, output);

    verify(jmapClient).downloadBlob(session, "blob-1", "report.pdf", "application/pdf", output);
  }

  private EmailDetailResult.EmailInfo emailWithAttachment() {
    return new EmailDetailResult.EmailInfo(
        "email-1",
        "Subject",
        "Preview",
        "2026-08-18T00:00:00Z",
        null,
        null,
        null,
        null,
        null,
        List.of(
            new EmailBodyPart(
                "part-1", "blob-1", 2048L, "report.pdf", "application/pdf", "attachment", null)),
        null);
  }

  private EmailAttachmentResult attachmentResult(EmailDetailResult.EmailInfo email) {
    return new EmailAttachmentResult(
        "account-1",
        "state",
        List.of(new EmailAttachmentResult.EmailInfo(email.id(), email.attachments())),
        List.of());
  }

  @Test
  void updatesEmailReadStatus() {
    when(jmapClient.setEmailsRead(session, List.of("email-1"), true))
        .thenReturn(new EmailUpdateResult(List.of("email-1"), List.of()));

    MailBatchUpdateResult result = mailService.updateReadStatuses(List.of("email-1"), true);

    assertThat(result.updatedIds()).containsExactly("email-1");
    verify(jmapClient).setEmailsRead(session, List.of("email-1"), true);
  }

  @Test
  void updatesEmailStarStatus() {
    when(jmapClient.setEmailsStarred(session, List.of("email-1"), true))
        .thenReturn(new EmailUpdateResult(List.of("email-1"), List.of()));

    MailBatchUpdateResult result = mailService.updateStarStatuses(List.of("email-1"), true);

    assertThat(result.updatedIds()).containsExactly("email-1");
    verify(jmapClient).setEmailsStarred(session, List.of("email-1"), true);
  }

  @Test
  void returnsPartialBatchReadResult() {
    when(jmapClient.setEmailsRead(session, List.of("email-1", "missing"), true))
        .thenReturn(
            new EmailUpdateResult(
                List.of("email-1"), List.of(new EmailUpdateResult.Failure("missing", "notFound"))));

    MailBatchUpdateResult result =
        mailService.updateReadStatuses(List.of("email-1", "missing"), true);

    assertThat(result.updatedIds()).containsExactly("email-1");
    assertThat(result.failed())
        .containsExactly(new MailBatchUpdateResult.Failure("missing", 2000, "邮件不存在"));
  }

  @Test
  void mapsUnclassifiedBatchFailureToUnavailable() {
    when(jmapClient.setEmailsStarred(session, List.of("email-1"), false))
        .thenReturn(
            new EmailUpdateResult(
                List.of(), List.of(new EmailUpdateResult.Failure("email-1", "forbidden"))));

    MailBatchUpdateResult result = mailService.updateStarStatuses(List.of("email-1"), false);

    assertThat(result.failed())
        .containsExactly(new MailBatchUpdateResult.Failure("email-1", 2004, "邮件服务暂时不可用"));
  }

  @Test
  void rejectsDuplicateBatchIds() {
    assertBusinessError(
        () -> mailService.updateReadStatuses(List.of("email-1", "email-1"), true),
        ErrorCode.BAD_REQUEST);

    verifyNoInteractions(jmapClient, sessionCache);
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
