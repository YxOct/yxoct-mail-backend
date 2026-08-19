package com.yxoct.mail.security;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yxoct.mail.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class UserAuthVersionFilterTest {

  @Mock private UserAuthVersionStore versionStore;
  @Mock private ApiSecurityErrorWriter errorWriter;
  @Mock private FilterChain filterChain;

  private UserAuthVersionFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    filter = new UserAuthVersionFilter(Optional.of(versionStore), errorWriter);
    request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer token");
    response = new MockHttpServletResponse();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void permitsATokenWithTheCurrentVersion() throws Exception {
    authenticate(3L);
    when(versionStore.currentVersion(7L)).thenReturn(OptionalLong.of(3L));

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(errorWriter, never()).write(response, ErrorCode.AUTHENTICATION_FAILED);
  }

  @Test
  void rejectsATokenWithAnOldVersion() throws Exception {
    authenticate(3L);
    when(versionStore.currentVersion(7L)).thenReturn(OptionalLong.of(4L));

    filter.doFilter(request, response, filterChain);

    verify(errorWriter).write(response, ErrorCode.AUTHENTICATION_FAILED);
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void rejectsWhenTheVersionStoreIsUnavailable() throws Exception {
    authenticate(3L);
    when(versionStore.currentVersion(7L)).thenThrow(new IllegalStateException("redis unavailable"));

    filter.doFilter(request, response, filterChain);

    verify(errorWriter).write(response, ErrorCode.AUTHENTICATION_FAILED);
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void rejectsATokenWithoutAVersionClaim() throws Exception {
    authenticate(null);

    filter.doFilter(request, response, filterChain);

    verify(errorWriter).write(response, ErrorCode.AUTHENTICATION_FAILED);
    verify(filterChain, never()).doFilter(request, response);
  }

  private void authenticate(Long version) {
    Jwt.Builder jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("7")
            .issuedAt(Instant.parse("2026-08-19T00:00:00Z"))
            .expiresAt(Instant.parse("2026-08-19T01:00:00Z"));
    if (version != null) {
      jwt.claim("userVersion", version);
    }
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build()));
  }
}
