package com.yxoct.mail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class MailBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(MailBackendApplication.class, args);
  }
}
