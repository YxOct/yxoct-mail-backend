package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.JmapClient;
import com.yxoct.mail.client.stalwart.JmapSessionCache;
import com.yxoct.mail.client.stalwart.dto.EmailUpdateResult;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.client.stalwart.dto.MailboxGetResult;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.mail.MailBatchUpdateResult;
import com.yxoct.mail.persistence.EmailRestoreRepository;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MailMoveService {

  private final JmapClient jmapClient;
  private final JmapSessionCache sessionCache;
  private final EmailRestoreRepository restoreRepository;

  public MailMoveService(
      JmapClient jmapClient,
      JmapSessionCache sessionCache,
      EmailRestoreRepository restoreRepository) {
    this.jmapClient = jmapClient;
    this.sessionCache = sessionCache;
    this.restoreRepository = restoreRepository;
  }

  public MailBatchUpdateResult moveEmails(List<String> ids, String targetMailboxId) {
    validateRequest(ids, targetMailboxId);
    List<String> requestedIds = List.copyOf(ids);
    JmapSession session = sessionCache.getSession();
    String accountId = requireMailAccountId(session);
    MailboxGetResult.MailboxInfo targetMailbox = requireTargetMailbox(session, targetMailboxId);
    if ("trash".equals(targetMailbox.role())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }

    Map<String, List<String>> updates = new LinkedHashMap<>();
    requestedIds.forEach(id -> updates.put(id, List.of(targetMailboxId)));
    EmailUpdateResult updateResult = jmapClient.setEmailMailboxes(session, updates);
    if (updateResult == null
        || updateResult.updatedIds() == null
        || updateResult.failures() == null) {
      throw new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE);
    }

    restoreRepository.deleteAll(accountId, updateResult.updatedIds());
    return new MailBatchUpdateResult(
        requestedIds.stream().filter(new HashSet<>(updateResult.updatedIds())::contains).toList(),
        updateResult.failures().stream()
            .map(
                failure -> {
                  ErrorCode errorCode = updateErrorCode(failure.type());
                  return new MailBatchUpdateResult.Failure(
                      failure.id(), errorCode.getCode(), errorCode.getMessage());
                })
            .toList());
  }

  private void validateRequest(List<String> ids, String targetMailboxId) {
    if (ids == null
        || ids.isEmpty()
        || ids.size() > 100
        || ids.stream().anyMatch(id -> id == null || id.isBlank())
        || new HashSet<>(ids).size() != ids.size()
        || targetMailboxId == null
        || targetMailboxId.isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
  }

  private String requireMailAccountId(JmapSession session) {
    String accountId =
        session == null || session.primaryAccounts() == null
            ? null
            : session.primaryAccounts().get("urn:ietf:params:jmap:mail");
    if (accountId == null || accountId.isBlank()) {
      throw new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE);
    }
    return accountId;
  }

  private MailboxGetResult.MailboxInfo requireTargetMailbox(
      JmapSession session, String targetMailboxId) {
    MailboxGetResult result = jmapClient.getMailboxes(session);
    if (result == null || result.list() == null) {
      throw new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE);
    }
    return result.list().stream()
        .filter(mailbox -> targetMailboxId.equals(mailbox.id()))
        .findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.MAILBOX_NOT_FOUND));
  }

  private ErrorCode updateErrorCode(String type) {
    return "notFound".equals(type) ? ErrorCode.EMAIL_NOT_FOUND : ErrorCode.MAIL_SERVICE_UNAVAILABLE;
  }
}
