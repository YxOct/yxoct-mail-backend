package com.yxoct.mail.security;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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

  public LoginRateLimiter(
      StringRedisTemplate redisTemplate,
      @Value("${app.security.login-rate-limit.max-failures:5}") int maxFailures,
      @Value("${app.security.login-rate-limit.window:PT15M}") Duration window) {
    this.redisTemplate = redisTemplate;
    this.maxFailures = maxFailures;
    this.window = window;
  }

  public void check(String emailAddress, String ipAddress) {
    if (increment(key("email", emailAddress)) > maxFailures
        || increment(key("ip", ipAddress)) > maxFailures) {
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
