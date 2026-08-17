package com.yxoct.mail.controller;

import com.yxoct.mail.client.stalwart.dto.JmapResponse;
import com.yxoct.mail.common.response.ApiResponse;
import com.yxoct.mail.service.MailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail")
public class MailController {

  private final MailService mailService;

  public MailController(MailService mailService) {
    this.mailService = mailService;
  }

  @GetMapping("/emails")
  public ApiResponse<JmapResponse> emails() {

    return ApiResponse.success(mailService.queryEmails());
  }
}
