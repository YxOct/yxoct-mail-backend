package com.yxoct.mail.security;

import com.yxoct.mail.config.AuthenticationProperties;
import com.yxoct.mail.domain.user.AccessTokenResponse;
import com.yxoct.mail.persistence.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  private final JwtEncoder jwtEncoder;
  private final AuthenticationProperties properties;
  private final Clock clock;

  public JwtTokenService(JwtEncoder jwtEncoder, AuthenticationProperties properties, Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.properties = properties;
    this.clock = clock;
  }

  public AccessTokenResponse issue(AuthenticatedUser user) {
    Instant issuedAt = clock.instant();
    Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(Long.toString(user.userId()))
            .claim("email", user.emailAddress())
            .claim("role", user.role().name())
            .build();
    String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    return new AccessTokenResponse(token, "Bearer", properties.accessTokenTtl().toSeconds());
  }
}
