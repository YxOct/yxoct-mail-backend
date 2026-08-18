package com.yxoct.mail.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yxoct.mail.client.stalwart.JmapSessionCache;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class StalwartHealthIndicatorTest {

  @Mock private JmapSessionCache sessionCache;

  @InjectMocks private StalwartHealthIndicator healthIndicator;

  @Test
  void reportsUpWhenSessionIsAvailable() {
    when(sessionCache.getSession()).thenReturn(org.mockito.Mockito.mock(JmapSession.class));

    assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void reportsDownWithoutSensitiveDetailsWhenSessionFails() {
    when(sessionCache.getSession())
        .thenThrow(new BusinessException(ErrorCode.MAIL_SERVICE_AUTHENTICATION_FAILED));

    var health = healthIndicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).hasSize(1).containsEntry("errorCode", 2006);
  }
}
