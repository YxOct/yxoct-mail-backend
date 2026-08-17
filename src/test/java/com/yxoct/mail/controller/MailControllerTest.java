package com.yxoct.mail.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yxoct.mail.service.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

  private void assertBadRequest(String parameter, String value) throws Exception {
    mockMvc
        .perform(get("/api/mail/mailboxes/inbox/emails").param(parameter, value))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(1000))
        .andExpect(jsonPath("$.message").value("请求参数错误"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }
}
