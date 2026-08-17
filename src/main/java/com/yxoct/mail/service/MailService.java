package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.JmapClient;
import com.yxoct.mail.client.stalwart.dto.EmailDetailResult;
import com.yxoct.mail.client.stalwart.dto.EmailListResult;
import com.yxoct.mail.client.stalwart.dto.EmailQueryResult;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.domain.mail.MailDetail;
import com.yxoct.mail.domain.mail.MailPage;
import com.yxoct.mail.domain.mail.MailSummary;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MailService {

  private final JmapClient jmapClient;

  public MailService(JmapClient jmapClient) {
    this.jmapClient = jmapClient;
  }

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

  public MailDetail getEmailDetail(String id) {

    JmapSession session = jmapClient.getSession();

    EmailDetailResult result = jmapClient.getEmailDetails(session, List.of(id));

    if (result.list().isEmpty()) {
      return null;
    }

    EmailDetailResult.EmailInfo email = result.list().get(0);

    return new MailDetail(
        email.id(),
        email.subject(),
        email.preview(),
        email.receivedAt(),
        email.bodyValues(),
        email.from(),
        email.to());
  }

  public List<com.yxoct.mail.domain.mail.Mailbox> getMailboxes() {

    JmapSession session = jmapClient.getSession();

    var result = jmapClient.getMailboxes(session);

    return result.list().stream()
        .map(
            mailbox ->
                new com.yxoct.mail.domain.mail.Mailbox(
                    mailbox.id(), mailbox.name(), mailbox.role()))
        .toList();
  }
}
