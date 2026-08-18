package com.yxoct.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yxoct.mail.client.stalwart.JmapClient;
import com.yxoct.mail.client.stalwart.JmapSessionCache;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.persistence.AuthenticatedUser;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;
import com.yxoct.mail.security.JwtTokenService;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class MailBackendApplicationTests {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JmapClient jmapClient;

  @Autowired private JmapSessionCache sessionCache;

  @Autowired private DataSource dataSource;

  @Autowired private SqlSessionFactory sqlSessionFactory;

  @Autowired private JwtTokenService jwtTokenService;

  @Value("${spring.http.clients.connect-timeout}")
  private Duration connectTimeout;

  @Value("${spring.http.clients.read-timeout}")
  private Duration readTimeout;

  @Test
  void contextLoads() {
    assertThat(connectTimeout).isEqualTo(Duration.ofSeconds(5));
    assertThat(readTimeout).isEqualTo(Duration.ofSeconds(10));
    assertThat(sessionCache).isNotNull();
    assertThat(sqlSessionFactory).isNotNull();
  }

  @Test
  void databaseConnectionAndMigrationsAreAvailable() throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT 1")) {
      assertThat(resultSet.next()).isTrue();
      assertThat(resultSet.getInt(1)).isEqualTo(1);
    }

    assertThat(queryForInt("SELECT COUNT(*) FROM email_restore_record")).isZero();
    assertThat(
            queryForInt(
                "SELECT COUNT(*) FROM flyway_schema_history "
                    + "WHERE version IN ('1', '2', '3', '4', '5', '6', '7', '8') AND success = TRUE"))
        .isEqualTo(8);
    assertThat(queryForInt("SELECT COUNT(*) FROM app_user")).isZero();
    assertThat(queryForInt("SELECT COUNT(*) FROM mail_account")).isZero();
    assertThat(queryForInt("SELECT COUNT(*) FROM email_address")).isZero();
    assertThat(queryForInt("SELECT COUNT(*) FROM user_mail_account")).isZero();
    assertThat(queryForInt("SELECT COUNT(*) FROM registration_invitation")).isZero();
  }

  private int queryForInt(String sql) throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      assertThat(resultSet.next()).isTrue();
      return resultSet.getInt(1);
    }
  }

  @Test
  void livenessDoesNotDependOnStalwart() throws Exception {
    mockMvc
        .perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));

    verifyNoInteractions(jmapClient);
  }

  @Test
  void readinessIncludesStalwart() throws Exception {
    when(jmapClient.getSession()).thenReturn(org.mockito.Mockito.mock(JmapSession.class));

    mockMvc
        .perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void rejectsUnauthenticatedMailRequestsWithApiError() throws Exception {
    mockMvc
        .perform(get("/api/mail/mailboxes"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(4000));
  }

  @Test
  void restrictsInvitationManagementToAdministrators() throws Exception {
    String userToken = tokenFor(UserRole.USER);
    String adminToken = tokenFor(UserRole.ADMIN);

    mockMvc
        .perform(get("/api/admin/invitations").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(4002));

    mockMvc
        .perform(get("/api/admin/invitations").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/admin/invitations")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"REGISTRATION\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void openApiDocumentationDescribesMailEndpoints() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value("3.0.1"))
        .andExpect(jsonPath("$.info.title").value("YxOct Mail API"))
        .andExpect(jsonPath("$.info.version").value("v1"))
        .andExpect(jsonPath("$.paths['/api/mail/mailboxes'].get").exists())
        .andExpect(jsonPath("$.paths['/api/mail/emails/read-status'].patch").exists())
        .andExpect(jsonPath("$.paths['/api/mail/emails/move'].post").exists())
        .andExpect(jsonPath("$.paths['/api/auth/register'].post").exists())
        .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
        .andExpect(jsonPath("$.paths['/api/admin/invitations'].post").exists())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(
            jsonPath("$.paths['/api/auth/register'].post.responses['409'].['$ref']")
                .value("#/components/responses/RegistrationConflict"))
        .andExpect(
            jsonPath("$.components.schemas.RegisterRequest.properties.password.writeOnly")
                .value(true))
        .andExpect(
            jsonPath("$.components.schemas.RegisterRequest.properties.password.format")
                .value("password"))
        .andExpect(
            jsonPath("$.components.schemas.ApiErrorResponse.properties.code.type").value("integer"))
        .andExpect(
            jsonPath(
                    "$.components.responses.BadRequest.content['application/json'].examples['code-1000'].value.code")
                .value(1000))
        .andExpect(
            jsonPath(
                    "$.paths['/api/mail/mailboxes/{mailboxId}/emails'].get.responses['400'].['$ref']")
                .value("#/components/responses/BadRequest"))
        .andExpect(
            jsonPath("$.paths['/api/mail/emails/{id}'].get.responses['404'].['$ref']")
                .value("#/components/responses/EmailNotFound"))
        .andExpect(
            jsonPath("$.paths['/api/mail/emails/{id}'].get.responses['502'].['$ref']")
                .value("#/components/responses/MailServiceUnavailable"))
        .andExpect(
            jsonPath("$.paths['/api/mail/emails/move'].post.responses['404'].['$ref']")
                .value("#/components/responses/MailboxNotFound"))
        .andExpect(
            jsonPath("$.paths['/api/mail/mailboxes'].get.responses['502'].['$ref']")
                .value("#/components/responses/MailServiceUnavailable"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/mail/emails/{emailId}/attachments/{blobId}'].get.responses['200'].content['application/octet-stream'].schema.format")
                .value("binary"));
  }

  private String tokenFor(UserRole role) {
    return jwtTokenService
        .issue(new AuthenticatedUser(42, "admin@yxoct.com", "unused", UserStatus.ACTIVE, role))
        .accessToken();
  }

  @Test
  void swaggerUiIsAvailableInTestProfile() throws Exception {
    mockMvc
        .perform(get("/swagger-ui.html"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/swagger-ui/index.html"));
  }
}
