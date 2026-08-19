package com.yxoct.mail.security;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.config.LoginRateLimitProperties;
import com.yxoct.mail.monitoring.MailOperationalMetrics;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
  private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
      new DefaultRedisScript<>(
          "local value = redis.call('INCR', KEYS[1]); "
              + "if value == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
              + "return value;",
          Long.class);

  private final StringRedisTemplate redisTemplate;
  private final int maxFailures;
  private final Duration window;
  private final MailOperationalMetrics metrics;

  public LoginRateLimiter(
      StringRedisTemplate redisTemplate,
      LoginRateLimitProperties properties,
      MailOperationalMetrics metrics) {
    this.redisTemplate = redisTemplate;
    this.maxFailures = properties.maxFailures();
    this.window = properties.window();
    this.metrics = metrics;
  }

  public void check(String emailAddress, String ipAddress) {
    if (increment(key("email", emailAddress)) > maxFailures) {
      metrics.recordLoginRateLimited("email");
      throw new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS);
    }
    if (increment(key("ip", ipAddress)) > maxFailures) {
      metrics.recordLoginRateLimited("ip");
      throw new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS);
    }
  }

  public void clear(String emailAddress, String ipAddress) {
    redisTemplate.delete(List.of(key("email", emailAddress), key("ip", ipAddress)));
  }

  private long increment(String key) {
    Long count =
        redisTemplate.execute(
            INCREMENT_SCRIPT, List.of(key), Long.toString(Math.max(1, window.toSeconds())));
    if (count == null) {
      throw new IllegalStateException("Redis returned no login rate-limit count");
    }
    return count;
  }

  private String key(String dimension, String value) {
    return "auth:login-failure:" + dimension + ":" + value;
  }
}
