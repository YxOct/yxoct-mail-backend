package com.yxoct.mail.client.stalwart;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class StalwartClientMetrics {

  static final String METRIC_NAME = "stalwart.client.requests";

  private final MeterRegistry meterRegistry;

  public StalwartClientMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public <T> T record(String operation, Supplier<T> request) {
    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = "success";
    try {
      return request.get();
    } catch (BusinessException exception) {
      outcome = outcome(exception.getErrorCode());
      throw exception;
    } catch (RuntimeException exception) {
      outcome = "unexpected";
      throw exception;
    } finally {
      sample.stop(
          Timer.builder(METRIC_NAME)
              .description("Stalwart JMAP client request duration")
              .tag("operation", operation)
              .tag("outcome", outcome)
              .register(meterRegistry));
    }
  }

  private String outcome(ErrorCode errorCode) {
    return switch (errorCode) {
      case MAIL_SERVICE_TIMEOUT -> "timeout";
      case MAIL_SERVICE_AUTHENTICATION_FAILED -> "authentication_failed";
      case MAIL_SERVICE_UNAVAILABLE -> "unavailable";
      default -> "business_error";
    };
  }
}
