package com.yxoct.mail.security;

import com.yxoct.mail.config.PrometheusProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class PrometheusScrapeAuthenticationFilter extends OncePerRequestFilter {

  private static final String ENDPOINT = "/actuator/prometheus";
  private static final String BASIC_PREFIX = "Basic ";
  private static final byte[] USERNAME_PREFIX = "prometheus:".getBytes(StandardCharsets.UTF_8);

  private final byte[] expectedToken;

  public PrometheusScrapeAuthenticationFilter(PrometheusProperties properties) {
    expectedToken = properties.scrapeToken().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !ENDPOINT.equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (expectedToken.length > 0
        && authorization != null
        && authorization.startsWith(BASIC_PREFIX)
        && validCredentials(authorization.substring(BASIC_PREFIX.length()))) {
      var authentication =
          UsernamePasswordAuthenticationToken.authenticated(
              "prometheus", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    filterChain.doFilter(request, response);
  }

  private boolean validCredentials(String encodedCredentials) {
    try {
      byte[] credentials = Base64.getDecoder().decode(encodedCredentials);
      byte[] expectedCredentials = new byte[USERNAME_PREFIX.length + expectedToken.length];
      System.arraycopy(USERNAME_PREFIX, 0, expectedCredentials, 0, USERNAME_PREFIX.length);
      System.arraycopy(
          expectedToken, 0, expectedCredentials, USERNAME_PREFIX.length, expectedToken.length);
      return MessageDigest.isEqual(expectedCredentials, credentials);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
