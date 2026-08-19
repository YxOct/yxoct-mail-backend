package com.yxoct.mail.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yxoct.mail.domain.user.AdminUserPage;
import com.yxoct.mail.domain.user.AdminUserSummary;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;
import com.yxoct.mail.service.AdminUserService;
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
@WebMvcTest(AdminUserController.class)
class AdminUserControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AdminUserService userService;

  @Test
  void listsUsersWithPagination() throws Exception {
    when(userService.list(2, 25)).thenReturn(new AdminUserPage(2, 25, 1, List.of(user())));

    mockMvc
        .perform(
            get("/api/admin/users")
                .param("page", "2")
                .param("size", "25")
                .principal(authentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.size").value(25))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].userId").value(7))
        .andExpect(jsonPath("$.data.items[0].primaryEmailAddress").value("alice@yxoct.com"))
        .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].stalwartAccountId").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].credentialCiphertext").doesNotExist());

    verify(userService).list(2, 25);
  }

  @Test
  void getsAUserById() throws Exception {
    when(userService.get(7)).thenReturn(user());

    mockMvc
        .perform(get("/api/admin/users/7").principal(authentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(7))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.userStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.data.mailAccountStatus").value("ACTIVE"));

    verify(userService).get(7);
  }

  @Test
  void validatesPaginationAndUserId() throws Exception {
    mockMvc
        .perform(get("/api/admin/users").param("page", "0").principal(authentication()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/admin/users").param("size", "101").principal(authentication()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/admin/users/0").principal(authentication()))
        .andExpect(status().isBadRequest());
  }

  private AdminUserSummary user() {
    return new AdminUserSummary(
        7,
        "alice@yxoct.com",
        "Alice",
        UserRole.USER,
        UserStatus.ACTIVE,
        9L,
        MailAccountStatus.ACTIVE,
        LocalDateTime.of(2026, 8, 19, 20, 0));
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
