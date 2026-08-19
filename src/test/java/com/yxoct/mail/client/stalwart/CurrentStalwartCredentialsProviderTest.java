package com.yxoct.mail.client.stalwart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.config.StalwartProperties;
import com.yxoct.mail.persistence.MailAccountCredential;
import com.yxoct.mail.persistence.MailAccountCredentialRepository;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.service.MailCredentialCipher;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class CurrentStalwartCredentialsProviderTest {

  @Mock private MailAccountCredentialRepository repository;
  @Mock private MailCredentialCipher credentialCipher;

  private CurrentStalwartCredentialsProvider provider;

  @BeforeEach
  void setUp() {
    provider =
        new CurrentStalwartCredentialsProvider(
            new StalwartProperties(
                URI.create("https://mail.example.com"),
                "health@example.com",
                "health-password",
                Duration.ofMinutes(5)),
            repository,
            credentialCipher);
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void resolvesAndCachesAuthenticatedUsersMailCredentials() {
    authenticateAs("42");
    when(repository.findOwnedPrimaryAccount(42))
        .thenReturn(
            Optional.of(
                new MailAccountCredential(
                    42, 7, "alice@example.com", MailAccountStatus.ACTIVE, "encrypted")));
    when(credentialCipher.decrypt("encrypted")).thenReturn("mail-password");

    StalwartCredentials first = provider.getCredentials();
    StalwartCredentials second = provider.getCredentials();

    assertThat(first)
        .isEqualTo(
            new StalwartCredentials("user:42:account:7", "alice@example.com", "mail-password"));
    assertThat(second).isSameAs(first);
    verify(repository).findOwnedPrimaryAccount(42);
    verify(credentialCipher).decrypt("encrypted");
  }

  @Test
  void rejectsMailAccountThatIsNotActive() {
    authenticateAs("42");
    when(repository.findOwnedPrimaryAccount(42))
        .thenReturn(
            Optional.of(
                new MailAccountCredential(
                    42, 7, "alice@example.com", MailAccountStatus.PROVISIONING, "encrypted")));

    assertThatThrownBy(provider::getCredentials)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MAIL_ACCOUNT_NOT_READY));
  }

  @Test
  void resolvesDifferentCredentialsAfterTheAuthenticatedUserChanges() {
    authenticateAs("42");
    when(repository.findOwnedPrimaryAccount(42))
        .thenReturn(
            Optional.of(
                new MailAccountCredential(
                    42, 7, "alice@example.com", MailAccountStatus.ACTIVE, "alice-encrypted")));
    when(credentialCipher.decrypt("alice-encrypted")).thenReturn("alice-password");

    assertThat(provider.getCredentials().username()).isEqualTo("alice@example.com");

    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    authenticateAs("84");
    when(repository.findOwnedPrimaryAccount(84))
        .thenReturn(
            Optional.of(
                new MailAccountCredential(
                    84, 9, "bob@example.com", MailAccountStatus.ACTIVE, "bob-encrypted")));
    when(credentialCipher.decrypt("bob-encrypted")).thenReturn("bob-password");

    assertThat(provider.getCredentials())
        .isEqualTo(new StalwartCredentials("user:84:account:9", "bob@example.com", "bob-password"));
  }

  @Test
  void usesConfiguredCredentialsOutsideJwtRequests() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("health", "password"));

    assertThat(provider.getCredentials())
        .isEqualTo(
            new StalwartCredentials(
                "configured:health@example.com", "health@example.com", "health-password"));
    verifyNoInteractions(repository, credentialCipher);
  }

  private void authenticateAs(String subject) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject)
            .issuedAt(Instant.parse("2026-08-18T00:00:00Z"))
            .expiresAt(Instant.parse("2026-08-18T01:00:00Z"))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(jwt, "token"));
  }
}
