package com.yxoct.mail.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.mail.MailBatchUpdateResult;
import com.yxoct.mail.service.MailMoveService;
import com.yxoct.mail.service.MailService;
import com.yxoct.mail.service.MailTrashService;
import java.util.List;
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

  @MockitoBean private MailMoveService mailMoveService;

  @MockitoBean private MailTrashService mailTrashService;

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
  void batchUpdatesEmailReadStatus() throws Exception {
    when(mailService.updateReadStatuses(List.of("email-1", "missing"), true))
        .thenReturn(
            new MailBatchUpdateResult(
                List.of("email-1"),
                List.of(new MailBatchUpdateResult.Failure("missing", 2000, "邮件不存在"))));

    mockMvc
        .perform(
            patch("/api/mail/emails/read-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"email-1\",\"missing\"],\"read\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedIds[0]").value("email-1"))
        .andExpect(jsonPath("$.data.failed[0].id").value("missing"))
        .andExpect(jsonPath("$.data.failed[0].code").value(2000));
  }

  @Test
  void batchUpdatesEmailStarStatus() throws Exception {
    when(mailService.updateStarStatuses(List.of("email-1", "email-2"), false))
        .thenReturn(new MailBatchUpdateResult(List.of("email-1", "email-2"), List.of()));

    mockMvc
        .perform(
            patch("/api/mail/emails/star-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"email-1\",\"email-2\"],\"starred\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedIds.length()").value(2))
        .andExpect(jsonPath("$.data.failed").isEmpty());
  }

  @Test
  void rejectsEmptyBatchIds() throws Exception {
    mockMvc
        .perform(
            patch("/api/mail/emails/read-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[],\"read\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(1000));
  }

  @Test
  void movesEmailsToMailbox() throws Exception {
    when(mailMoveService.moveEmails(List.of("email-1", "email-2"), "archive"))
        .thenReturn(new MailBatchUpdateResult(List.of("email-1", "email-2"), List.of()));

    mockMvc
        .perform(
            post("/api/mail/emails/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"email-1\",\"email-2\"],\"targetMailboxId\":\"archive\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedIds.length()").value(2));
  }

  @Test
  void rejectsMoveWithoutTargetMailbox() throws Exception {
    mockMvc
        .perform(
            post("/api/mail/emails/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"email-1\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(1000));
  }

  @Test
  void batchMovesEmailsToTrash() throws Exception {
    when(mailTrashService.moveEmailsToTrash(List.of("email-1", "email-2")))
        .thenReturn(new MailBatchUpdateResult(List.of("email-1", "email-2"), List.of()));

    mockMvc
        .perform(
            post("/api/mail/emails/trash")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"email-1\",\"email-2\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedIds.length()").value(2));
  }

  @Test
  void rejectsEmptyBatchRestoreRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/mail/emails/restore")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[]}"))
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
