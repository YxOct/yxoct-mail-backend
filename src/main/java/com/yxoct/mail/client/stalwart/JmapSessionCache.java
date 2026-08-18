package com.yxoct.mail.client.stalwart;

import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.config.StalwartProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JmapSessionCache {

  private final JmapClient jmapClient;
  private final Duration timeToLive;
  private final Clock clock;
  private final StalwartCredentialsProvider credentialsProvider;
  private final Object refreshLock = new Object();
  private final Map<String, CachedSession> cachedSessions = new HashMap<>();

  @Autowired
  public JmapSessionCache(
      JmapClient jmapClient,
      StalwartProperties properties,
      Clock clock,
      StalwartCredentialsProvider credentialsProvider) {
    this(jmapClient, properties.sessionCacheTtl(), clock, credentialsProvider);
  }

  JmapSessionCache(
      JmapClient jmapClient,
      Duration timeToLive,
      Clock clock,
      StalwartCredentialsProvider credentialsProvider) {
    this.jmapClient = jmapClient;
    this.timeToLive = timeToLive;
    this.clock = clock;
    this.credentialsProvider = credentialsProvider;
  }

  public JmapSession getSession() {
    String cacheKey = credentialsProvider.getCredentials().cacheKey();
    Instant now = clock.instant();
    synchronized (refreshLock) {
      CachedSession current = cachedSessions.get(cacheKey);
      if (isValid(current, now)) {
        return current.session();
      }

      JmapSession session = jmapClient.getSession();
      cachedSessions.put(cacheKey, new CachedSession(session, now.plus(timeToLive)));
      return session;
    }
  }

  public void invalidate() {
    String cacheKey = credentialsProvider.getCredentials().cacheKey();
    synchronized (refreshLock) {
      cachedSessions.remove(cacheKey);
    }
  }

  private boolean isValid(CachedSession session, Instant now) {
    return session != null && now.isBefore(session.expiresAt());
  }

  private record CachedSession(JmapSession session, Instant expiresAt) {}
}
