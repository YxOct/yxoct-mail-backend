package com.yxoct.mail.controller;

import com.yxoct.mail.client.stalwart.JmapClient;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test/jmap")
public class JmapTestController {

  private final JmapClient jmapClient;
  private final String username;
  private final String password;

  public JmapTestController(
      JmapClient jmapClient,
      @Value("${STALWART_TEST_USERNAME}") String username,
      @Value("${STALWART_TEST_PASSWORD}") String password) {
    this.jmapClient = jmapClient;
    this.username = username;
    this.password = password;
  }

  @GetMapping("/session")
  public ApiResponse<JmapSession> getSession() {
    return ApiResponse.success(jmapClient.getSession(username, password));
  }
}
