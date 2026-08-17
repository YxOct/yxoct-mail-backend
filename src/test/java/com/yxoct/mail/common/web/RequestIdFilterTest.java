package com.yxoct.mail.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void preservesValidRequestIdDuringRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdContext.HEADER_NAME, "request-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> requestIdInChain = new AtomicReference<>();

    filter.doFilter(
        request,
        response,
        (ignoredRequest, ignoredResponse) -> requestIdInChain.set(RequestIdContext.current()));

    assertThat(requestIdInChain).hasValue("request-123");
    assertThat(response.getHeader(RequestIdContext.HEADER_NAME)).isEqualTo("request-123");
    assertThat(RequestIdContext.current()).isNull();
  }

  @Test
  void replacesUnsafeRequestId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdContext.HEADER_NAME, "unsafe request id");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {});

    assertThat(response.getHeader(RequestIdContext.HEADER_NAME))
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }
}
