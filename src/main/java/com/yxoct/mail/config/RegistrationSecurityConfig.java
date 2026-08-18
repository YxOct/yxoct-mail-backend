package com.yxoct.mail.config;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class RegistrationSecurityConfig {

  private static final String ARGON2_ID = "argon2@SpringSecurity_v5_8";

  @Bean
  PasswordEncoder passwordEncoder() {
    return new DelegatingPasswordEncoder(
        ARGON2_ID, Map.of(ARGON2_ID, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
  }

  @Bean
  SecureRandom registrationSecureRandom() {
    return new SecureRandom();
  }

  @Bean
  Clock applicationClock() {
    return Clock.systemUTC();
  }
}
