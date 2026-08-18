package com.yxoct.mail.client.stalwart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yxoct.mail.client.stalwart.dto.JmapSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JmapSessionCacheTest {

  @Mock private JmapClient jmapClient;
  @Mock private JmapSession firstSession;
  @Mock private JmapSession secondSession;

  private MutableClock clock;
  private JmapSessionCache cache;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-08-18T00:00:00Z"));
    cache = new JmapSessionCache(jmapClient, Duration.ofMinutes(1), clock);
  }

  @Test
  void reusesSessionBeforeExpiry() {
    when(jmapClient.getSession()).thenReturn(firstSession);

    assertThat(cache.getSession()).isSameAs(firstSession);
    assertThat(cache.getSession()).isSameAs(firstSession);

    verify(jmapClient).getSession();
  }

  @Test
  void refreshesSessionAfterExpiry() {
    when(jmapClient.getSession()).thenReturn(firstSession, secondSession);

    assertThat(cache.getSession()).isSameAs(firstSession);
    clock.advance(Duration.ofMinutes(1));
    assertThat(cache.getSession()).isSameAs(secondSession);

    verify(jmapClient, times(2)).getSession();
  }

  @Test
  void refreshesSessionAfterInvalidation() {
    when(jmapClient.getSession()).thenReturn(firstSession, secondSession);

    assertThat(cache.getSession()).isSameAs(firstSession);
    cache.invalidate();
    assertThat(cache.getSession()).isSameAs(secondSession);
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
