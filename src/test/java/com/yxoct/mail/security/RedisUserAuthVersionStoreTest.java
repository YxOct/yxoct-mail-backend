package com.yxoct.mail.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yxoct.mail.persistence.mapper.UserAuthVersionMapper;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisUserAuthVersionStoreTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private UserAuthVersionMapper mapper;

  private RedisUserAuthVersionStore store;

  @BeforeEach
  void setUp() {
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    store = new RedisUserAuthVersionStore(redisTemplate, mapper);
  }

  @Test
  void returnsCachedVersionWithoutQueryingMySql() {
    when(valueOperations.get("auth:user-version:7")).thenReturn("3");

    assertThat(store.currentVersion(7)).isEqualTo(OptionalLong.of(3));

    verify(mapper, never()).findVersion(7);
  }

  @Test
  void loadsAndCachesVersionAfterACacheMiss() {
    when(valueOperations.get("auth:user-version:7")).thenReturn(null);
    when(mapper.findVersion(7)).thenReturn(3L);

    assertThat(store.currentVersion(7)).isEqualTo(OptionalLong.of(3));

    verify(valueOperations).set("auth:user-version:7", "3");
  }

  @Test
  void returnsEmptyWhenTheUserNoLongerExists() {
    when(valueOperations.get("auth:user-version:7")).thenReturn(null);
    when(mapper.findVersion(7)).thenReturn(null);

    assertThat(store.currentVersion(7)).isEmpty();
  }

  @Test
  void discardsAnInvalidCachedVersionAndReloadsIt() {
    when(valueOperations.get("auth:user-version:7")).thenReturn("invalid");
    when(mapper.findVersion(7)).thenReturn(4L);

    assertThat(store.currentVersion(7)).isEqualTo(OptionalLong.of(4));

    verify(redisTemplate).delete("auth:user-version:7");
    verify(valueOperations).set("auth:user-version:7", "4");
  }

  @Test
  void updatesLoginVersionWithoutAllowingAConcurrentDowngrade() {
    store.setVersionIfGreater(7, 4);

    verify(redisTemplate)
        .execute(
            org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
            org.mockito.ArgumentMatchers.eq(java.util.List.of("auth:user-version:7")),
            org.mockito.ArgumentMatchers.eq("4"));
  }
}
