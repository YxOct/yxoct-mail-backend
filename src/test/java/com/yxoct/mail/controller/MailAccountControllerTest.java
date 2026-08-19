package com.yxoct.mail.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yxoct.mail.domain.mail.MailAccountSettings;
import com.yxoct.mail.service.MailAccountSettingsService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(MailAccountController.class)
class MailAccountControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private MailAccountSettingsService settingsService;

  @Test
  void updatesAnOwnedMailAccountDisplayName() throws Exception {
    when(settingsService.updateDisplayName("1", 2, "Alice Zhang"))
        .thenReturn(new MailAccountSettings(2, "Alice Zhang"));

    mockMvc
        .perform(
            patch("/api/mail/accounts/2")
                .principal(authentication(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Alice Zhang\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.mailAccountId").value(2))
        .andExpect(jsonPath("$.data.displayName").value("Alice Zhang"));

    verify(settingsService).updateDisplayName("1", 2, "Alice Zhang");
  }

  @Test
  void validatesTheAccountIdAndDisplayName() throws Exception {
    mockMvc
        .perform(
            patch("/api/mail/accounts/0")
                .principal(authentication(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(1000));
  }

  private JwtAuthenticationToken authentication(long userId) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(Long.toString(userId))
            .issuedAt(Instant.parse("2026-08-19T00:00:00Z"))
            .expiresAt(Instant.parse("2026-08-19T23:00:00Z"))
            .build();
    return new JwtAuthenticationToken(jwt);
  }
}
