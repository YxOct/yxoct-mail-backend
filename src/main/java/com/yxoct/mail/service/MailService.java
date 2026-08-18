package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.JmapClient;
import com.yxoct.mail.client.stalwart.JmapSessionCache;
import com.yxoct.mail.client.stalwart.dto.EmailAddress;
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
import com.yxoct.mail.domain.mail.MailBatchUpdateResult;
import com.yxoct.mail.domain.mail.MailDetail;
import com.yxoct.mail.domain.mail.MailPage;
import com.yxoct.mail.domain.mail.MailQueryFilter;
import com.yxoct.mail.domain.mail.MailSort;
import com.yxoct.mail.domain.mail.MailSummary;
import com.yxoct.mail.domain.mail.Mailbox;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class MailService {

  private final JmapClient jmapClient;
  private final JmapSessionCache sessionCache;

  public MailService(JmapClient jmapClient, JmapSessionCache sessionCache) {
    this.jmapClient = jmapClient;
    this.sessionCache = sessionCache;
  }

  /** 分页查询邮件列表 */
  public MailPage<MailSummary> queryEmails(
      String mailboxId, int page, int size, MailQueryFilter filter, MailSort sort) {

    if (filter == null || sort == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }

    long calculatedPosition = (long) (page - 1) * size;
    if (calculatedPosition > Integer.MAX_VALUE) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }

    int position = (int) calculatedPosition;
    JmapSession session = sessionCache.getSession();

    EmailQueryResult queryResult =
        jmapClient.queryEmails(session, mailboxId, position, size, filter, sort);
    if (queryResult == null) {
      throw mailServiceUnavailable();
    }

    List<String> ids = requireList(queryResult.ids());
    if (ids.isEmpty()) {
      return new MailPage<>(
          page, size, queryResult.total() == null ? 0 : queryResult.total(), List.of());
    }

    EmailListResult listResult = jmapClient.getEmailSummaries(session, ids);
    if (listResult == null) {
      throw mailServiceUnavailable();
    }

    List<MailSummary> items =
        requireList(listResult.list()).stream()
            .map(
                email ->
                    new MailSummary(
                        email.id(),
                        email.subject(),
                        email.preview(),
                        email.receivedAt(),
                        hasKeyword(email.keywords(), "$seen"),
                        hasKeyword(email.keywords(), "$flagged")))
            .toList();

    return new MailPage<>(page, size, queryResult.total() == null ? 0 : queryResult.total(), items);
  }

  /** 获取邮件详情 */
  public MailDetail getEmailDetail(String id) {

    JmapSession session = sessionCache.getSession();

    EmailDetailResult result = jmapClient.getEmailDetails(session, List.of(id));

    if (result == null || result.list() == null) {
      throw mailServiceUnavailable();
    }

    if ((result.notFound() != null && result.notFound().contains(id)) || result.list().isEmpty()) {
      throw new BusinessException(ErrorCode.EMAIL_NOT_FOUND);
    }

    EmailDetailResult.EmailInfo email = requireList(result.list()).getFirst();

    return new MailDetail(
        email.id(),
        email.subject(),
        email.preview(),
        email.receivedAt(),
        convertAddresses(email.from()),
        convertAddresses(email.to()),
        extractBody(email),
        hasKeyword(email.keywords(), "$seen"),
        hasKeyword(email.keywords(), "$flagged"));
  }

  /** 批量更新邮件已读状态 */
  public MailBatchUpdateResult updateReadStatuses(List<String> ids, boolean read) {
    validateUpdateIds(ids);
    return toBatchUpdateResult(
        jmapClient.setEmailsRead(sessionCache.getSession(), List.copyOf(ids), read));
  }

  /** 批量更新邮件星标状态 */
  public MailBatchUpdateResult updateStarStatuses(List<String> ids, boolean starred) {
    validateUpdateIds(ids);
    return toBatchUpdateResult(
        jmapClient.setEmailsStarred(sessionCache.getSession(), List.copyOf(ids), starred));
  }

  /** 获取邮箱列表 */
  public List<Mailbox> getMailboxes() {

    JmapSession session = sessionCache.getSession();

    MailboxGetResult result = jmapClient.getMailboxes(session);
    if (result == null) {
      throw mailServiceUnavailable();
    }

    return requireList(result.list()).stream()
        .map(mailbox -> new Mailbox(mailbox.id(), mailbox.name(), mailbox.role()))
        .toList();
  }

  /** 转换发件人/收件人 */
  private List<MailAddress> convertAddresses(List<EmailAddress> addresses) {

    if (addresses == null) {
      return List.of();
    }

    return requireList(addresses).stream()
        .map(address -> new MailAddress(address.name(), address.email()))
        .toList();
  }

  /** 提取正文 */
  private String extractBody(EmailDetailResult.EmailInfo email) {

    if (email.bodyValues() == null || email.bodyValues().isEmpty()) {
      return null;
    }

    String partId = findPartId(email.textBody());

    if (partId == null) {
      partId = findPartId(email.htmlBody());
    }

    if (partId == null) {
      return null;
    }

    EmailBodyValue body = email.bodyValues().get(partId);

    return body == null ? null : body.value();
  }

  private String findPartId(List<EmailBodyPart> bodyParts) {

    if (bodyParts == null) {
      return null;
    }

    return bodyParts.stream()
        .filter(Objects::nonNull)
        .map(EmailBodyPart::partId)
        .filter(Objects::nonNull)
        .filter(partId -> !partId.isBlank())
        .findFirst()
        .orElse(null);
  }

  private boolean hasKeyword(java.util.Map<String, Boolean> keywords, String keyword) {
    return keywords != null && Boolean.TRUE.equals(keywords.get(keyword));
  }

  private void validateUpdateIds(List<String> ids) {
    if (ids == null
        || ids.isEmpty()
        || ids.size() > 100
        || ids.stream().anyMatch(id -> id == null || id.isBlank())
        || new HashSet<>(ids).size() != ids.size()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
  }

  private MailBatchUpdateResult toBatchUpdateResult(EmailUpdateResult result) {
    if (result == null || result.updatedIds() == null || result.failures() == null) {
      throw mailServiceUnavailable();
    }

    return new MailBatchUpdateResult(
        result.updatedIds(),
        result.failures().stream()
            .map(
                failure -> {
                  ErrorCode errorCode = updateErrorCode(failure.type());
                  return new MailBatchUpdateResult.Failure(
                      failure.id(), errorCode.getCode(), errorCode.getMessage());
                })
            .toList());
  }

  private ErrorCode updateErrorCode(String type) {
    return "notFound".equals(type) ? ErrorCode.EMAIL_NOT_FOUND : ErrorCode.MAIL_SERVICE_UNAVAILABLE;
  }

  private <T> List<T> requireList(List<T> values) {

    if (values == null || values.stream().anyMatch(Objects::isNull)) {
      throw mailServiceUnavailable();
    }

    return values;
  }

  private BusinessException mailServiceUnavailable() {
    return new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE);
  }
}
