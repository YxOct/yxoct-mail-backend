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
import com.yxoct.mail.client.stalwart.dto.EmailMailboxResult;
import com.yxoct.mail.client.stalwart.dto.EmailUpdateResult;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.client.stalwart.dto.MailboxGetResult;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.mail.MailBatchUpdateResult;
import com.yxoct.mail.persistence.EmailRestoreRepository;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailTrashServiceTest {

  @Mock private JmapClient jmapClient;
  @Mock private JmapSessionCache sessionCache;
  @Mock private EmailRestoreRepository restoreRepository;

  private MailTrashService service;
  private JmapSession session;

  @BeforeEach
  void setUp() {
    service = new MailTrashService(jmapClient, sessionCache, restoreRepository);
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
  void movesEmailToTrashAfterSavingAllOriginalMailboxes() {
    when(jmapClient.getMailboxes(session)).thenReturn(mailboxes("archive"));
    Map<String, Boolean> originalMailboxIds = new LinkedHashMap<>();
    originalMailboxIds.put("inbox", true);
    originalMailboxIds.put("archive", true);
    when(jmapClient.getEmailMailboxes(session, List.of("email-1")))
        .thenReturn(
            new EmailMailboxResult(
                "account-1",
                "state",
                List.of(new EmailMailboxResult.EmailInfo("email-1", originalMailboxIds)),
                List.of()));
    when(restoreRepository.findMailboxIds("account-1", "email-1")).thenReturn(Optional.empty());
    when(restoreRepository.saveIfAbsent("account-1", "email-1", List.of("inbox", "archive")))
        .thenReturn(true);
    when(jmapClient.setEmailMailboxes(session, Map.of("email-1", List.of("trash"))))
        .thenReturn(new EmailUpdateResult(List.of("email-1"), List.of()));

    service.moveEmailToTrash("email-1");

    verify(restoreRepository).saveIfAbsent("account-1", "email-1", List.of("inbox", "archive"));
    verify(jmapClient).setEmailMailboxes(session, Map.of("email-1", List.of("trash")));
  }

  @Test
  void keepsFirstRestoreLocationWhenMovingEmailToTrashAgain() {
    when(jmapClient.getMailboxes(session)).thenReturn(mailboxes());
    when(jmapClient.getEmailMailboxes(session, List.of("email-1")))
        .thenReturn(
            new EmailMailboxResult(
                "account-1",
                "state",
                List.of(new EmailMailboxResult.EmailInfo("email-1", Map.of("trash", true))),
                List.of()));
    when(restoreRepository.findMailboxIds("account-1", "email-1"))
        .thenReturn(Optional.of(List.of("archive")));
    when(jmapClient.setEmailMailboxes(session, Map.of("email-1", List.of("trash"))))
        .thenReturn(new EmailUpdateResult(List.of("email-1"), List.of()));

    service.moveEmailToTrash("email-1");

    verify(restoreRepository, never()).saveIfAbsent("account-1", "email-1", List.of("inbox"));
  }

  @Test
  void removesNewRestoreRecordForExplicitRemoteFailure() {
    when(jmapClient.getMailboxes(session)).thenReturn(mailboxes());
    when(jmapClient.getEmailMailboxes(session, List.of("email-1", "email-2")))
        .thenReturn(
            new EmailMailboxResult(
                "account-1",
                "state",
                List.of(
                    new EmailMailboxResult.EmailInfo("email-1", Map.of("inbox", true)),
                    new EmailMailboxResult.EmailInfo("email-2", Map.of("inbox", true))),
                List.of()));
    when(restoreRepository.findMailboxIds("account-1", "email-1")).thenReturn(Optional.empty());
    when(restoreRepository.findMailboxIds("account-1", "email-2")).thenReturn(Optional.empty());
    when(restoreRepository.saveIfAbsent("account-1", "email-1", List.of("inbox"))).thenReturn(true);
    when(restoreRepository.saveIfAbsent("account-1", "email-2", List.of("inbox"))).thenReturn(true);
    Map<String, List<String>> updates = new LinkedHashMap<>();
    updates.put("email-1", List.of("trash"));
    updates.put("email-2", List.of("trash"));
    when(jmapClient.setEmailMailboxes(session, updates))
        .thenReturn(
            new EmailUpdateResult(
                List.of("email-1"),
                List.of(new EmailUpdateResult.Failure("email-2", "forbidden"))));

    MailBatchUpdateResult result = service.moveEmailsToTrash(List.of("email-1", "email-2"));

    assertThat(result.updatedIds()).containsExactly("email-1");
    assertThat(result.failed())
        .containsExactly(new MailBatchUpdateResult.Failure("email-2", 2004, "邮件服务暂时不可用"));
    verify(restoreRepository).deleteAll("account-1", List.of("email-2"));
  }

  @Test
  void preservesRestoreRecordWhenRemoteMoveOutcomeIsUnknown() {
    when(jmapClient.getMailboxes(session)).thenReturn(mailboxes());
    when(jmapClient.getEmailMailboxes(session, List.of("email-1")))
        .thenReturn(
            new EmailMailboxResult(
                "account-1",
                "state",
                List.of(new EmailMailboxResult.EmailInfo("email-1", Map.of("inbox", true))),
                List.of()));
    when(restoreRepository.findMailboxIds("account-1", "email-1")).thenReturn(Optional.empty());
    when(restoreRepository.saveIfAbsent("account-1", "email-1", List.of("inbox"))).thenReturn(true);
    when(jmapClient.setEmailMailboxes(session, Map.of("email-1", List.of("trash"))))
        .thenThrow(new BusinessException(ErrorCode.MAIL_SERVICE_TIMEOUT));

    assertBusinessError(() -> service.moveEmailToTrash("email-1"), ErrorCode.MAIL_SERVICE_TIMEOUT);

    verify(restoreRepository, never()).deleteAll("account-1", List.of("email-1"));
  }

  @Test
  void restoresEmailToInboxWhenOriginalMailboxNoLongerExists() {
    when(restoreRepository.findMailboxIds("account-1", "email-1"))
        .thenReturn(Optional.of(List.of("deleted-mailbox")));
    when(jmapClient.getMailboxes(session)).thenReturn(mailboxes());
    when(jmapClient.setEmailMailboxes(session, Map.of("email-1", List.of("inbox"))))
        .thenReturn(new EmailUpdateResult(List.of("email-1"), List.of()));

    service.restoreEmail("email-1");

    verify(jmapClient).setEmailMailboxes(session, Map.of("email-1", List.of("inbox")));
    verify(restoreRepository).deleteAll("account-1", List.of("email-1"));
  }

  @Test
  void reportsMissingRestoreRecordWithoutCallingJmap() {
    when(restoreRepository.findMailboxIds("account-1", "email-1")).thenReturn(Optional.empty());

    MailBatchUpdateResult result = service.restoreEmails(List.of("email-1"));

    assertThat(result.updatedIds()).isEmpty();
    assertThat(result.failed())
        .containsExactly(new MailBatchUpdateResult.Failure("email-1", 2001, "邮件恢复记录不存在"));
    verifyNoInteractions(jmapClient);
  }

  @Test
  void rejectsDuplicateIdsBeforeCallingDependencies() {
    assertBusinessError(
        () -> service.moveEmailsToTrash(List.of("email-1", "email-1")), ErrorCode.BAD_REQUEST);

    verifyNoInteractions(jmapClient, sessionCache, restoreRepository);
  }

  private MailboxGetResult mailboxes(String... additionalIds) {
    List<MailboxGetResult.MailboxInfo> values = new ArrayList<>();
    values.add(new MailboxGetResult.MailboxInfo("inbox", "Inbox", "inbox"));
    values.add(new MailboxGetResult.MailboxInfo("trash", "Trash", "trash"));
    for (String id : additionalIds) {
      values.add(new MailboxGetResult.MailboxInfo(id, id, null));
    }
    return new MailboxGetResult("account-1", "state", values, List.of());
  }

  private void assertBusinessError(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(errorCode);
  }
}
