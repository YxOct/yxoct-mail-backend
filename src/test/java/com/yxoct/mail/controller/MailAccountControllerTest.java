package com.yxoct.mail.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yxoct.mail.domain.mail.EmailAliasResult;
import com.yxoct.mail.domain.mail.MailAccountEmailAddress;
import com.yxoct.mail.domain.mail.MailAccountSettings;
import com.yxoct.mail.persistence.entity.EmailAddressType;
import com.yxoct.mail.service.EmailAliasService;
import com.yxoct.mail.service.MailAccountAddressService;
import com.yxoct.mail.service.MailAccountSettingsService;
import java.time.Instant;
import java.util.List;
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
  @MockitoBean private EmailAliasService aliasService;
  @MockitoBean private MailAccountAddressService addressService;

  @Test
  void listsAddressesForAnOwnedMailAccount() throws Exception {
    when(addressService.list("1", 2))
        .thenReturn(
            List.of(
                new MailAccountEmailAddress(10, "alice@yxoct.com", EmailAddressType.PRIMARY),
                new MailAccountEmailAddress(11, "hello@yxoct.com", EmailAddressType.ALIAS)));

    mockMvc
        .perform(get("/api/mail/accounts/2/addresses").principal(authentication(1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data[0].id").value(10))
        .andExpect(jsonPath("$.data[0].emailAddress").value("alice@yxoct.com"))
        .andExpect(jsonPath("$.data[0].addressType").value("PRIMARY"))
        .andExpect(jsonPath("$.data[1].id").value(11))
        .andExpect(jsonPath("$.data[1].emailAddress").value("hello@yxoct.com"))
        .andExpect(jsonPath("$.data[1].addressType").value("ALIAS"));

    verify(addressService).list("1", 2);
  }

  @Test
  void addsAnAliasToAnOwnedMailAccount() throws Exception {
    when(aliasService.create("1", 2, "yxi-token", "hello"))
        .thenReturn(new EmailAliasResult(2, "hello@yxoct.com", EmailAddressType.ALIAS));

    mockMvc
        .perform(
            post("/api/mail/accounts/2/aliases")
                .principal(authentication(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invitationCode\":\"yxi-token\",\"emailLocalPart\":\"hello\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.mailAccountId").value(2))
        .andExpect(jsonPath("$.data.emailAddress").value("hello@yxoct.com"))
        .andExpect(jsonPath("$.data.addressType").value("ALIAS"));

    verify(aliasService).create("1", 2, "yxi-token", "hello");
  }

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
