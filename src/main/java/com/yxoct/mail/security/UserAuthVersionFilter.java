package com.yxoct.mail.security;

import com.yxoct.mail.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class UserAuthVersionFilter extends OncePerRequestFilter {

  private final UserAuthVersionStore versionStore;
  private final ApiSecurityErrorWriter errorWriter;

  public UserAuthVersionFilter(
      Optional<UserAuthVersionStore> versionStore, ApiSecurityErrorWriter errorWriter) {
    this.versionStore = versionStore.orElse(null);
    this.errorWriter = errorWriter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
        || request.getHeader("Authorization") == null) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      if (versionStore == null) {
        reject(response);
        return;
      }
      Object userVersionClaim = jwtAuthentication.getToken().getClaim("userVersion");
      long userId = Long.parseLong(jwtAuthentication.getToken().getSubject());
      OptionalLong currentVersion = versionStore.currentVersion(userId);
      if (!(userVersionClaim instanceof Number userVersion)
          || currentVersion.isEmpty()
          || currentVersion.getAsLong() != userVersion.longValue()) {
        reject(response);
        return;
      }
      if (Boolean.TRUE.equals(jwtAuthentication.getToken().getClaim("mustChangePassword"))
          && !isPasswordChangeAllowed(request)) {
        errorWriter.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
        return;
      }
      filterChain.doFilter(request, response);
    } catch (RuntimeException exception) {
      reject(response);
    }
  }

  private boolean isPasswordChangeAllowed(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.equals("/api/auth/password")
        || path.equals("/api/auth/logout")
        || path.equals("/api/auth/me");
  }

  private void reject(HttpServletResponse response) throws IOException {
    SecurityContextHolder.clearContext();
    errorWriter.write(response, ErrorCode.AUTHENTICATION_FAILED);
  }
}
