package com.yxoct.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yxoct.mail.client.stalwart.JmapClient;
import com.yxoct.mail.client.stalwart.JmapSessionCache;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
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
                    + "WHERE version = '1' AND success = TRUE"))
        .isEqualTo(1);
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
}
