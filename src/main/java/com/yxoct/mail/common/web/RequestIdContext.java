package com.yxoct.mail.common.web;

import org.slf4j.MDC;

public final class RequestIdContext {

  public static final String HEADER_NAME = "X-Request-Id";
  public static final String MDC_KEY = "requestId";

  private RequestIdContext() {}

  public static String current() {
    return MDC.get(MDC_KEY);
  }
}
