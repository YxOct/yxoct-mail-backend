package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.JmapClient;
import com.yxoct.mail.client.stalwart.dto.EmailGetResult;
import com.yxoct.mail.client.stalwart.dto.EmailQueryResult;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.client.stalwart.dto.MailboxGetResult;
import com.yxoct.mail.domain.mail.MailDetail;
import com.yxoct.mail.domain.mail.MailSummary;
import com.yxoct.mail.domain.mail.Mailbox;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MailService {

  private final JmapClient jmapClient;

  public MailService(JmapClient jmapClient) {
    this.jmapClient = jmapClient;
  }

  public List<MailSummary> queryEmails() {

    JmapSession session = jmapClient.getSession();

    EmailQueryResult result = jmapClient.queryEmails(session);

    return result.ids().stream().map(MailSummary::new).toList();
  }

  public MailDetail getEmailDetail(String id) {

    JmapSession session = jmapClient.getSession();

    EmailGetResult result = jmapClient.getEmails(session, List.of(id));

    if (result.list().isEmpty()) {
      return null;
    }

    return convert(result.list().get(0));
  }

  private MailDetail convert(Map<String, Object> mail) {

    return new MailDetail(
        (String) mail.get("id"),
        (String) mail.get("subject"),
        (String) mail.get("preview"),
        Instant.parse((String) mail.get("receivedAt")));
  }

  public List<Mailbox> getMailboxes() {

    JmapSession session = jmapClient.getSession();

    MailboxGetResult result = jmapClient.getMailboxes(session);

    return result.list().stream()
        .map(mailbox -> new Mailbox(mailbox.id(), mailbox.name(), mailbox.role()))
        .toList();
  }
}
