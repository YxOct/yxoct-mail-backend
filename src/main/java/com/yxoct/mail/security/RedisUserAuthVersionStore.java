package com.yxoct.mail.security;

import com.yxoct.mail.persistence.mapper.UserAuthVersionMapper;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisUserAuthVersionStore implements UserAuthVersionStore {

  private static final String KEY_PREFIX = "auth:user-version:";
  private static final DefaultRedisScript<Long> SET_IF_GREATER_SCRIPT =
      new DefaultRedisScript<>(
          "local current = redis.call('GET', KEYS[1]); "
              + "if (not current) or tonumber(ARGV[1]) > tonumber(current) then "
              + "redis.call('SET', KEYS[1], ARGV[1]); return 1; end; return 0;",
          Long.class);

  private final StringRedisTemplate redisTemplate;
  private final UserAuthVersionMapper mapper;

  public RedisUserAuthVersionStore(
      StringRedisTemplate redisTemplate, UserAuthVersionMapper mapper) {
    this.redisTemplate = redisTemplate;
    this.mapper = mapper;
  }

  @Override
  public OptionalLong currentVersion(long userId) {
    String key = key(userId);
    String cached = redisTemplate.opsForValue().get(key);
    if (cached != null) {
      try {
        return OptionalLong.of(Long.parseLong(cached));
      } catch (NumberFormatException exception) {
        redisTemplate.delete(key);
      }
    }
    Long version = mapper.findVersion(userId);
    if (version == null) {
      return OptionalLong.empty();
    }
    setVersion(userId, version);
    return OptionalLong.of(version);
  }

  @Override
  public void setVersion(long userId, long version) {
    redisTemplate.opsForValue().set(key(userId), Long.toString(version));
  }

  @Override
  public void setVersionIfGreater(long userId, long version) {
    redisTemplate.execute(SET_IF_GREATER_SCRIPT, List.of(key(userId)), Long.toString(version));
  }

  private String key(long userId) {
    return KEY_PREFIX + userId;
  }
}
