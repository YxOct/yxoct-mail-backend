package com.yxoct.mail.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.LoginRequest;
import com.yxoct.mail.domain.user.RegisterRequest;
import com.yxoct.mail.domain.user.RegistrationResult;
import com.yxoct.mail.domain.user.TokenPairResponse;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.service.LoginService;
import com.yxoct.mail.service.RefreshTokenService;
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
  @MockitoBean private LoginService loginService;
  @MockitoBean private RefreshTokenService refreshTokenService;

  @Test
  void registersAnInvitedUser() throws Exception {
    RegisterRequest request = new RegisterRequest(INVITATION, "alice", null, PASSWORD);
    when(registrationService.register(request))
        .thenReturn(
            new RegistrationResult(
                1L, 2L, "alice@yxoct.com", "alice", MailAccountStatus.PROVISIONING));

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
        .andExpect(jsonPath("$.data.displayName").value("alice"))
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
    RegisterRequest request = new RegisterRequest(INVITATION, "alice", null, PASSWORD);
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

  @Test
  void logsInWithPrimaryEmailAddress() throws Exception {
    LoginRequest request = new LoginRequest("alice@yxoct.com", PASSWORD);
    when(loginService.login(request))
        .thenReturn(
            new TokenPairResponse("signed-access-token", "Bearer", 900, "a".repeat(43), 2_592_000));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "emailAddress": "alice@yxoct.com",
                      "password": "%s"
                    }
                    """
                        .formatted(PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.accessToken").value("signed-access-token"))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.accessExpiresIn").value(900))
        .andExpect(jsonPath("$.data.refreshToken").value("a".repeat(43)))
        .andExpect(jsonPath("$.data.refreshExpiresIn").value(2_592_000));

    verify(loginService).login(request);
  }

  @Test
  void returnsDedicatedErrorForInvalidLoginCredentials() throws Exception {
    LoginRequest request = new LoginRequest("alice@yxoct.com", PASSWORD);
    when(loginService.login(request))
        .thenThrow(new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "emailAddress": "alice@yxoct.com",
                      "password": "%s"
                    }
                    """
                        .formatted(PASSWORD)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(4005))
        .andExpect(jsonPath("$.message").value("邮箱地址或密码错误"));
  }

  @Test
  void rotatesRefreshToken() throws Exception {
    String refreshToken = "a".repeat(43);
    TokenPairResponse response =
        new TokenPairResponse("new-access-token", "Bearer", 900, "b".repeat(43), 2_592_000);
    when(refreshTokenService.refresh(refreshToken)).thenReturn(response);

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
        .andExpect(jsonPath("$.data.refreshToken").value("b".repeat(43)));

    verify(refreshTokenService).refresh(refreshToken);
  }

  @Test
  void logsOutWithRefreshToken() throws Exception {
    String refreshToken = "a".repeat(43);

    mockMvc
        .perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    verify(refreshTokenService).revoke(refreshToken);
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
