package com.yxoct.mail.controller;

import com.yxoct.mail.common.response.ApiResponse;
import com.yxoct.mail.domain.mail.MailDetail;
import com.yxoct.mail.domain.mail.MailPage;
import com.yxoct.mail.domain.mail.MailSummary;
import com.yxoct.mail.domain.mail.Mailbox;
import com.yxoct.mail.service.MailService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail")
public class MailController {

  private final MailService mailService;

  public MailController(MailService mailService) {
    this.mailService = mailService;
  }

  @GetMapping("/mailboxes/{mailboxId}/emails")
  public ApiResponse<MailPage<MailSummary>> emails(
      @PathVariable String mailboxId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {

    return ApiResponse.success(mailService.queryEmails(mailboxId, page, size));
  }

  @GetMapping("/emails/{id}")
  public ApiResponse<MailDetail> detail(@PathVariable String id) {

    return ApiResponse.success(mailService.getEmailDetail(id));
  }

  @GetMapping("/mailboxes")
  public ApiResponse<List<Mailbox>> mailboxes() {

    return ApiResponse.success(mailService.getMailboxes());
  }
}
