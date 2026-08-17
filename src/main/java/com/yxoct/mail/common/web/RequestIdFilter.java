package com.yxoct.mail.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  private static final int MAX_REQUEST_ID_LENGTH = 100;
  private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]+");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String previousRequestId = RequestIdContext.current();
    String requestId = resolveRequestId(request.getHeader(RequestIdContext.HEADER_NAME));

    MDC.put(RequestIdContext.MDC_KEY, requestId);
    response.setHeader(RequestIdContext.HEADER_NAME, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      if (previousRequestId == null) {
        MDC.remove(RequestIdContext.MDC_KEY);
      } else {
        MDC.put(RequestIdContext.MDC_KEY, previousRequestId);
      }
    }
  }

  private String resolveRequestId(String suppliedRequestId) {
    if (suppliedRequestId != null
        && suppliedRequestId.length() <= MAX_REQUEST_ID_LENGTH
        && VALID_REQUEST_ID.matcher(suppliedRequestId).matches()) {
      return suppliedRequestId;
    }
    return UUID.randomUUID().toString();
  }
}
