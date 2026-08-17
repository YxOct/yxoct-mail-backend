package com.yxoct.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class MailBackendApplicationTests {

  @Value("${spring.http.clients.connect-timeout}")
  private Duration connectTimeout;

  @Value("${spring.http.clients.read-timeout}")
  private Duration readTimeout;

  @Test
  void contextLoads() {
    assertThat(connectTimeout).isEqualTo(Duration.ofSeconds(5));
    assertThat(readTimeout).isEqualTo(Duration.ofSeconds(10));
  }
}
