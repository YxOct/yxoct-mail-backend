package com.yxoct.mail.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.service.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(MailController.class)
class MailControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MailService mailService;

  @Test
  void rejectsPageBelowOne() throws Exception {
    assertBadRequest("page", "0");
  }

  @Test
  void rejectsSizeBelowOne() throws Exception {
    assertBadRequest("size", "0");
  }

  @Test
  void rejectsSizeAboveMaximum() throws Exception {
    assertBadRequest("size", "101");
  }

  @Test
  void rejectsNonNumericPage() throws Exception {
    assertBadRequest("page", "invalid");
  }

  @Test
  void returnsGatewayTimeoutWhenMailServiceTimesOut() throws Exception {
    when(mailService.getMailboxes())
        .thenThrow(new BusinessException(ErrorCode.MAIL_SERVICE_TIMEOUT));

    mockMvc
        .perform(get("/api/mail/mailboxes"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value(2005))
        .andExpect(jsonPath("$.message").value("邮件服务响应超时"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void updatesEmailReadStatus() throws Exception {
    mockMvc
        .perform(
            patch("/api/mail/emails/email-1/read-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"read\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    verify(mailService).updateReadStatus("email-1", true);
  }

  @Test
  void rejectsMissingReadStatus() throws Exception {
    mockMvc
        .perform(
            patch("/api/mail/emails/email-1/read-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(1000));
  }

  private void assertBadRequest(String parameter, String value) throws Exception {
    mockMvc
        .perform(get("/api/mail/mailboxes/inbox/emails").param(parameter, value))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(1000))
        .andExpect(jsonPath("$.message").value("请求参数错误"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }
}
