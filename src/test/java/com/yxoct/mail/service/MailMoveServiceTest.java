package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yxoct.mail.client.stalwart.JmapClient;
import com.yxoct.mail.client.stalwart.JmapSessionCache;
import com.yxoct.mail.client.stalwart.dto.EmailUpdateResult;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.client.stalwart.dto.MailboxGetResult;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.mail.MailBatchUpdateResult;
import com.yxoct.mail.persistence.EmailRestoreRepository;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailMoveServiceTest {

  @Mock private JmapClient jmapClient;
  @Mock private JmapSessionCache sessionCache;
  @Mock private EmailRestoreRepository restoreRepository;

  private MailMoveService service;
  private JmapSession session;

  @BeforeEach
  void setUp() {
    service = new MailMoveService(jmapClient, sessionCache, restoreRepository);
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
  }

  @Test
  void movesEmailsAndClearsRestoreRecordsForSuccessfulUpdates() {
    when(sessionCache.getSession()).thenReturn(session);
    when(jmapClient.getMailboxes(session)).thenReturn(mailboxes());
    Map<String, List<String>> updates = new LinkedHashMap<>();
    updates.put("email-1", List.of("archive"));
    updates.put("email-2", List.of("archive"));
    when(jmapClient.setEmailMailboxes(session, updates))
        .thenReturn(new EmailUpdateResult(List.of("email-1", "email-2"), List.of()));

    MailBatchUpdateResult result = service.moveEmails(List.of("email-1", "email-2"), "archive");

    assertThat(result.updatedIds()).containsExactly("email-1", "email-2");
    assertThat(result.failed()).isEmpty();
    verify(restoreRepository).deleteAll("account-1", List.of("email-1", "email-2"));
  }

  @Test
  void clearsOnlySuccessfulRestoreRecordsForPartialMove() {
    when(sessionCache.getSession()).thenReturn(session);
    when(jmapClient.getMailboxes(session)).thenReturn(mailboxes());
    Map<String, List<String>> updates = new LinkedHashMap<>();
    updates.put("email-1", List.of("archive"));
    updates.put("missing", List.of("archive"));
    when(jmapClient.setEmailMailboxes(session, updates))
        .thenReturn(
            new EmailUpdateResult(
                List.of("email-1"), List.of(new EmailUpdateResult.Failure("missing", "notFound"))));

    MailBatchUpdateResult result = service.moveEmails(List.of("email-1", "missing"), "archive");

    assertThat(result.updatedIds()).containsExactly("email-1");
    assertThat(result.failed())
        .containsExactly(new MailBatchUpdateResult.Failure("missing", 2000, "邮件不存在"));
    verify(restoreRepository).deleteAll("account-1", List.of("email-1"));
  }

  @Test
  void rejectsUnknownTargetMailbox() {
    when(sessionCache.getSession()).thenReturn(session);
    when(jmapClient.getMailboxes(session)).thenReturn(mailboxes());

    assertBusinessError(
        () -> service.moveEmails(List.of("email-1"), "missing"), ErrorCode.MAILBOX_NOT_FOUND);

    verify(jmapClient, never()).setEmailMailboxes(session, Map.of("email-1", List.of("missing")));
    verifyNoInteractions(restoreRepository);
  }

  @Test
  void requiresTrashEndpointWhenTargetIsTrash() {
    when(sessionCache.getSession()).thenReturn(session);
    when(jmapClient.getMailboxes(session)).thenReturn(mailboxes());

    assertBusinessError(
        () -> service.moveEmails(List.of("email-1"), "trash"), ErrorCode.BAD_REQUEST);

    verifyNoInteractions(restoreRepository);
  }

  @Test
  void preservesRestoreRecordsWhenRemoteOutcomeIsUnknown() {
    when(sessionCache.getSession()).thenReturn(session);
    when(jmapClient.getMailboxes(session)).thenReturn(mailboxes());
    when(jmapClient.setEmailMailboxes(session, Map.of("email-1", List.of("archive"))))
        .thenThrow(new BusinessException(ErrorCode.MAIL_SERVICE_TIMEOUT));

    assertBusinessError(
        () -> service.moveEmails(List.of("email-1"), "archive"), ErrorCode.MAIL_SERVICE_TIMEOUT);

    verifyNoInteractions(restoreRepository);
  }

  @Test
  void rejectsDuplicateIdsBeforeCallingDependencies() {
    assertBusinessError(
        () -> service.moveEmails(List.of("email-1", "email-1"), "archive"), ErrorCode.BAD_REQUEST);

    verifyNoInteractions(jmapClient, sessionCache, restoreRepository);
  }

  private MailboxGetResult mailboxes() {
    return new MailboxGetResult(
        "account-1",
        "state",
        List.of(
            new MailboxGetResult.MailboxInfo("inbox", "Inbox", "inbox"),
            new MailboxGetResult.MailboxInfo("archive", "Archive", null),
            new MailboxGetResult.MailboxInfo("trash", "Trash", "trash")),
        List.of());
  }

  private void assertBusinessError(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(errorCode);
  }
}
