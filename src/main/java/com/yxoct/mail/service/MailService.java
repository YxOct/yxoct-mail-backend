package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.JmapClient;
import com.yxoct.mail.client.stalwart.dto.JmapResponse;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MailService {

  private final JmapClient jmapClient;

  public MailService(JmapClient jmapClient) {
    this.jmapClient = jmapClient;
  }

  public JmapResponse queryEmails() {

    JmapSession session = jmapClient.getSession();

    return jmapClient.queryEmails(session);
  }

  public JmapResponse getEmailDetail(List<String> ids) {

    JmapSession session = jmapClient.getSession();

    return jmapClient.getEmails(session, ids);
  }
}
