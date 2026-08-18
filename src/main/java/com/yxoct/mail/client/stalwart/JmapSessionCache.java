package com.yxoct.mail.client.stalwart;

import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.config.StalwartProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JmapSessionCache {

  private final JmapClient jmapClient;
  private final Duration timeToLive;
  private final Clock clock;
  private final Object refreshLock = new Object();

  private volatile CachedSession cachedSession;

  @Autowired
  public JmapSessionCache(JmapClient jmapClient, StalwartProperties properties) {
    this(jmapClient, properties.sessionCacheTtl(), Clock.systemUTC());
  }

  JmapSessionCache(JmapClient jmapClient, Duration timeToLive, Clock clock) {
    this.jmapClient = jmapClient;
    this.timeToLive = timeToLive;
    this.clock = clock;
  }

  public JmapSession getSession() {
    Instant now = clock.instant();
    CachedSession current = cachedSession;
    if (isValid(current, now)) {
      return current.session();
    }

    synchronized (refreshLock) {
      current = cachedSession;
      now = clock.instant();
      if (isValid(current, now)) {
        return current.session();
      }

      JmapSession session = jmapClient.getSession();
      cachedSession = new CachedSession(session, now.plus(timeToLive));
      return session;
    }
  }

  public void invalidate() {
    synchronized (refreshLock) {
      cachedSession = null;
    }
  }

  private boolean isValid(CachedSession session, Instant now) {
    return session != null && now.isBefore(session.expiresAt());
  }

  private record CachedSession(JmapSession session, Instant expiresAt) {}
}
