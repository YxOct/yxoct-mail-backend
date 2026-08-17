package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.JmapClient;
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
import java.util.Map;
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

    EmailListResult listResult = jmapClient.getEmailSummaries(session, queryResult.ids());

    List<MailSummary> items =
        listResult.list().stream()
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

    if (result.list().isEmpty()) {
      throw new BusinessException(ErrorCode.EMAIL_NOT_FOUND);
    }

    EmailDetailResult.EmailInfo email = result.list().get(0);

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

    return result.list().stream()
        .map(mailbox -> new Mailbox(mailbox.id(), mailbox.name(), mailbox.role()))
        .toList();
  }

  /** 转换发件人/收件人 */
  private List<MailAddress> convertAddresses(List<Map<String, Object>> addresses) {

    if (addresses == null) {
      return List.of();
    }

    return addresses.stream()
        .map(
            address -> new MailAddress((String) address.get("name"), (String) address.get("email")))
        .toList();
  }

  /** 提取正文 */
  private String extractBody(EmailDetailResult.EmailInfo email) {

    if (email.bodyValues() == null || email.bodyValues().isEmpty()) {
      return null;
    }

    String partId = null;

    if (email.textBody() != null && !email.textBody().isEmpty()) {

      partId = String.valueOf(email.textBody().get(0).get("partId"));
    }

    if (partId == null && email.htmlBody() != null && !email.htmlBody().isEmpty()) {

      partId = String.valueOf(email.htmlBody().get(0).get("partId"));
    }

    if (partId == null) {
      return null;
    }

    Object body = email.bodyValues().get(partId);

    if (body instanceof Map<?, ?> map) {

      Object value = map.get("value");

      return value == null ? null : value.toString();
    }

    return null;
  }
}
