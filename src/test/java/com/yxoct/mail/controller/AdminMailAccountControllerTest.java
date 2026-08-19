package com.yxoct.mail.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yxoct.mail.domain.mail.AdminMailAccountDriftEntry;
import com.yxoct.mail.domain.mail.AdminMailAccountDriftPage;
import com.yxoct.mail.domain.mail.AdminMailAccountProvisioningEntry;
import com.yxoct.mail.domain.mail.AdminMailAccountProvisioningPage;
import com.yxoct.mail.persistence.entity.MailAccountDriftType;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.service.AdminMailAccountService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(AdminMailAccountController.class)
class AdminMailAccountControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AdminMailAccountService service;

  @Test
  void listsDetectedDrifts() throws Exception {
    when(service.listDrifts(1, 20))
        .thenReturn(
            new AdminMailAccountDriftPage(
                1,
                20,
                1,
                List.of(
                    new AdminMailAccountDriftEntry(
                        9,
                        7,
                        "alice@yxoct.com",
                        MailAccountStatus.ACTIVE,
                        "account-9",
                        MailAccountDriftType.REMOTE_ACCOUNT_MISSING,
                        null,
                        LocalDateTime.of(2026, 8, 19, 20, 0)))));

    mockMvc
        .perform(get("/api/admin/mail-accounts/drifts").principal(authentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].driftType").value("REMOTE_ACCOUNT_MISSING"));

    verify(service).listDrifts(1, 20);
  }

  @Test
  void listsProvisioningIssues() throws Exception {
    var entry =
        new AdminMailAccountProvisioningEntry(
            9,
            7,
            "alice@yxoct.com",
            MailAccountStatus.FAILED,
            3,
            "MANAGEMENT_REQUEST_FAILED",
            LocalDateTime.of(2026, 8, 19, 20, 0),
            null,
            LocalDateTime.of(2026, 8, 19, 19, 0));
    when(service.listProvisioningIssues(2, 25))
        .thenReturn(new AdminMailAccountProvisioningPage(2, 25, 1, List.of(entry)));

    mockMvc
        .perform(
            get("/api/admin/mail-accounts/provisioning")
                .param("page", "2")
                .param("size", "25")
                .principal(authentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].mailAccountId").value(9))
        .andExpect(jsonPath("$.data.items[0].status").value("FAILED"))
        .andExpect(
            jsonPath("$.data.items[0].lastProvisioningError").value("MANAGEMENT_REQUEST_FAILED"))
        .andExpect(jsonPath("$.data.items[0].credentialCiphertext").doesNotExist());

    verify(service).listProvisioningIssues(2, 25);
  }

  @Test
  void schedulesProvisioningRetryWithAdministratorId() throws Exception {
    mockMvc
        .perform(post("/api/admin/mail-accounts/9/retry-provisioning").principal(authentication()))
        .andExpect(status().isOk());

    verify(service).retryProvisioning(42, 9);
  }

  @Test
  void validatesPaginationAndMailAccountId() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/mail-accounts/provisioning")
                .param("page", "0")
                .principal(authentication()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/admin/mail-accounts/provisioning")
                .param("size", "101")
                .principal(authentication()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(post("/api/admin/mail-accounts/0/retry-provisioning").principal(authentication()))
        .andExpect(status().isBadRequest());
  }

  private JwtAuthenticationToken authentication() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("42")
            .issuedAt(Instant.parse("2026-08-19T00:00:00Z"))
            .expiresAt(Instant.parse("2026-08-19T23:00:00Z"))
            .claim("role", "ADMIN")
            .build();
    return new JwtAuthenticationToken(jwt);
  }
}
