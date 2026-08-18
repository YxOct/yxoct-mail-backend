package com.yxoct.mail.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.RegisterRequest;
import com.yxoct.mail.domain.user.RegistrationResult;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(AuthController.class)
class AuthControllerTest {

  private static final String INVITATION = "valid-invitation-token-value";
  private static final String PASSWORD = "correct horse battery staple";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private RegistrationService registrationService;

  @Test
  void registersAnInvitedUser() throws Exception {
    RegisterRequest request = new RegisterRequest(INVITATION, "alice", PASSWORD);
    when(registrationService.register(request))
        .thenReturn(
            new RegistrationResult(1L, 2L, "alice@yxoct.com", MailAccountStatus.PROVISIONING));

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("alice", PASSWORD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.userId").value(1))
        .andExpect(jsonPath("$.data.mailAccountId").value(2))
        .andExpect(jsonPath("$.data.emailAddress").value("alice@yxoct.com"))
        .andExpect(jsonPath("$.data.status").value("PROVISIONING"));

    verify(registrationService).register(request);
  }

  @Test
  void rejectsInvalidRegistrationInput() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("invalid..name", "short")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(1000));
  }

  @Test
  void returnsConflictWhenEmailAddressIsUnavailable() throws Exception {
    RegisterRequest request = new RegisterRequest(INVITATION, "alice", PASSWORD);
    when(registrationService.register(request))
        .thenThrow(new BusinessException(ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE));

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("alice", PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(3004));
  }

  private String registerJson(String localPart, String password) {
    return """
        {
          "invitationCode": "%s",
          "emailLocalPart": "%s",
          "password": "%s"
        }
        """
        .formatted(INVITATION, localPart, password);
  }
}
