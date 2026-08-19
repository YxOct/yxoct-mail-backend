package com.yxoct.mail.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  @Test
  void validationLogDoesNotContainRejectedValues() throws Exception {
    String rejectedPassword = "do-not-log-this-password";
    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new Object(), "loginRequest");
    bindingResult.addError(
        new FieldError("loginRequest", "password", rejectedPassword, false, null, null, null));
    bindingResult.addError(
        new FieldError("loginRequest", "emailAddress", "invalid-address", false, null, null, null));
    Method method = TestHandler.class.getDeclaredMethod("login", Object.class);
    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

    Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      new GlobalExceptionHandler().handleValidationException(exception);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    assertThat(appender.list).hasSize(1);
    String message = appender.list.getFirst().getFormattedMessage();
    assertThat(message)
        .isEqualTo("Request validation failed: fields=[emailAddress, password]")
        .doesNotContain(rejectedPassword)
        .doesNotContain("invalid-address");
  }

  private static final class TestHandler {

    @SuppressWarnings("unused")
    void login(Object request) {}
  }
}
