package com.yxoct.mail.service;

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
import com.yxoct.mail.domain.mail.MailAddress;
import com.yxoct.mail.domain.mail.MailDetail;
import com.yxoct.mail.domain.mail.MailPage;
import com.yxoct.mail.domain.mail.MailSummary;
import com.yxoct.mail.domain.mail.Mailbox;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class MailService {

  private final JmapClient jmapClient;

  public MailService(JmapClient jmapClient) {
    this.jmapClient = jmapClient;
  }

  /** 分页查询邮件列表 */
  public MailPage<MailSummary> queryEmails(String mailboxId, int page, int size) {

    JmapSession session = jmapClient.getSession();

    int position = (page - 1) * size;

    EmailQueryResult queryResult = jmapClient.queryEmails(session, mailboxId, position, size);
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
                        email.id(), email.subject(), email.preview(), email.receivedAt()))
            .toList();

    return new MailPage<>(page, size, queryResult.total() == null ? 0 : queryResult.total(), items);
  }

  /** 获取邮件详情 */
  public MailDetail getEmailDetail(String id) {

    JmapSession session = jmapClient.getSession();

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
        extractBody(email));
  }

  /** 获取邮箱列表 */
  public List<Mailbox> getMailboxes() {

    JmapSession session = jmapClient.getSession();

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
