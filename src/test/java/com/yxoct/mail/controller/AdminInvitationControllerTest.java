package com.yxoct.mail.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yxoct.mail.domain.user.CreatedRegistrationInvitation;
import com.yxoct.mail.domain.user.RegistrationInvitationSummary;
import com.yxoct.mail.persistence.entity.RegistrationInvitationPurpose;
import com.yxoct.mail.persistence.entity.RegistrationInvitationStatus;
import com.yxoct.mail.service.RegistrationInvitationService;
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
@WebMvcTest(AdminInvitationController.class)
class AdminInvitationControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private RegistrationInvitationService invitationService;

  @Test
  void createsInvitationForAuthenticatedAdministrator() throws Exception {
    Instant expiresAt = Instant.parse("2026-08-20T00:00:00Z");
    when(invitationService.create(RegistrationInvitationPurpose.REGISTRATION, 42L))
        .thenReturn(new CreatedRegistrationInvitation(7, "yxiToken", expiresAt));

    mockMvc
        .perform(
            post("/api/admin/invitations")
                .principal(authentication(42))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"REGISTRATION\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(7))
        .andExpect(jsonPath("$.data.token").value("yxiToken"));

    verify(invitationService).create(RegistrationInvitationPurpose.REGISTRATION, 42L);
  }

  @Test
  void listsInvitationMetadataWithoutTokenHashes() throws Exception {
    Instant expiresAt = Instant.parse("2026-08-20T00:00:00Z");
    when(invitationService.list(25))
        .thenReturn(
            List.of(
                new RegistrationInvitationSummary(
                    7,
                    RegistrationInvitationStatus.PENDING,
                    RegistrationInvitationPurpose.REGISTRATION,
                    expiresAt,
                    null,
                    null,
                    42L,
                    null,
                    null,
                    Instant.parse("2026-08-19T00:00:00Z"))));

    mockMvc
        .perform(get("/api/admin/invitations").param("limit", "25").principal(authentication(42)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(7))
        .andExpect(jsonPath("$.data[0].status").value("PENDING"))
        .andExpect(jsonPath("$.data[0].createdByUserId").value(42))
        .andExpect(jsonPath("$.data[0].token").doesNotExist());
  }

  @Test
  void revokesInvitationWithAdministratorAudit() throws Exception {
    mockMvc
        .perform(delete("/api/admin/invitations/7").principal(authentication(42)))
        .andExpect(status().isOk());

    verify(invitationService).revoke(7, 42L);
  }

  @Test
  void validatesInvitationPurposeAndListLimit() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/invitations")
                .principal(authentication(42))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/admin/invitations").param("limit", "101").principal(authentication(42)))
        .andExpect(status().isBadRequest());
  }

  private JwtAuthenticationToken authentication(long userId) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(Long.toString(userId))
            .issuedAt(Instant.parse("2026-08-19T00:00:00Z"))
            .expiresAt(Instant.parse("2026-08-19T01:00:00Z"))
            .claim("role", "ADMIN")
            .build();
    return new JwtAuthenticationToken(jwt);
  }
}
