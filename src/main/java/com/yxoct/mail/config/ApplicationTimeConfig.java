package com.yxoct.mail.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ApplicationTimeConfig {

  @Bean
  Clock applicationClock(ApplicationTimeProperties properties) {
    return Clock.system(properties.timeZone());
  }
}
