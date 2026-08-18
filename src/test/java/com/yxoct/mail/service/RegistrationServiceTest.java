package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.CreatedRegistrationInvitation;
import com.yxoct.mail.domain.user.RegisterRequest;
import com.yxoct.mail.domain.user.RegistrationResult;
import com.yxoct.mail.persistence.entity.AppUserEntity;
import com.yxoct.mail.persistence.entity.EmailAddressEntity;
import com.yxoct.mail.persistence.entity.MailAccountEntity;
import com.yxoct.mail.persistence.entity.RegistrationInvitationEntity;
import com.yxoct.mail.persistence.entity.RegistrationInvitationPurpose;
import com.yxoct.mail.persistence.entity.RegistrationInvitationStatus;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.mapper.AppUserMapper;
import com.yxoct.mail.persistence.mapper.EmailAddressMapper;
import com.yxoct.mail.persistence.mapper.MailAccountMapper;
import com.yxoct.mail.persistence.mapper.RegistrationInvitationMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class RegistrationServiceTest {

  private static final String PASSWORD = "correct horse battery staple";

  @Autowired private RegistrationService registrationService;
  @Autowired private RegistrationInvitationService invitationService;
  @Autowired private RegistrationInvitationMapper invitationMapper;
  @Autowired private InvitationTokenCodec invitationTokenCodec;
  @Autowired private AppUserMapper appUserMapper;
  @Autowired private EmailAddressMapper emailAddressMapper;
  @Autowired private MailAccountMapper mailAccountMapper;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Clock clock;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM registration_invitation");
    jdbcTemplate.update("DELETE FROM user_mail_account");
    jdbcTemplate.update("DELETE FROM email_address");
    jdbcTemplate.update("DELETE FROM mail_account");
    jdbcTemplate.update("DELETE FROM app_user");
  }

  @Test
  void registersNormalizedPrimaryAddressAndConsumesInvitation() {
    CreatedRegistrationInvitation invitation = invitationService.create();
    LocalDateTime beforeRegistration = applicationNow();

    assertThat(invitation.token()).matches("^yxi[A-Za-z0-9_-]{22}$");

    RegistrationResult result =
        registrationService.register(
            new RegisterRequest(invitation.token(), "Alice", "  Alice Zhang  ", PASSWORD));
    LocalDateTime afterRegistration = applicationNow();

    assertThat(result.emailAddress()).isEqualTo("alice@yxoct.com");
    assertThat(result.displayName()).isEqualTo("Alice Zhang");
    assertThat(result.status().name()).isEqualTo("PROVISIONING");

    MailAccountEntity mailAccount = mailAccountMapper.selectById(result.mailAccountId());
    assertThat(mailAccount.getDisplayName()).isEqualTo("Alice Zhang");
    assertThat(mailAccount.getNextProvisioningAt())
        .isBetween(beforeRegistration, afterRegistration);

    AppUserEntity user = appUserMapper.selectById(result.userId());
    assertThat(user.getPasswordHash()).startsWith("{argon2@SpringSecurity_v5_8}");
    assertThat(user.getPasswordHash()).doesNotContain(PASSWORD);
    assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
    assertThat(user.getRole()).isEqualTo(UserRole.USER);

    EmailAddressEntity emailAddress =
        emailAddressMapper.selectOne(
            Wrappers.<EmailAddressEntity>lambdaQuery()
                .eq(EmailAddressEntity::getNormalizedAddress, "alice@yxoct.com"));
    assertThat(emailAddress.getAddress()).isEqualTo("alice@yxoct.com");
    assertThat(emailAddress.getNormalizedAddress()).isEqualTo("alice@yxoct.com");

    RegistrationInvitationEntity consumed = invitationMapper.selectById(invitation.id());
    assertThat(consumed.getStatus()).isEqualTo(RegistrationInvitationStatus.USED);
    assertThat(consumed.getPurpose()).isEqualTo(RegistrationInvitationPurpose.REGISTRATION);
    assertThat(consumed.getUsedByUserId()).isEqualTo(result.userId());
    assertThat(consumed.getUsedAt()).isNotNull();
    assertThat(consumed.getTokenHash()).isEqualTo(invitationTokenCodec.hash(invitation.token()));
    assertThat(consumed.getTokenHash()).hasSize(64).doesNotContain(invitation.token());
  }

  @Test
  void rejectsReusingAnInvitation() {
    CreatedRegistrationInvitation invitation = invitationService.create();
    registrationService.register(new RegisterRequest(invitation.token(), "alice", null, PASSWORD));

    assertThatThrownBy(
            () ->
                registrationService.register(
                    new RegisterRequest(invitation.token(), "bob", null, PASSWORD)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVITATION_ALREADY_USED));
  }

  @Test
  void defaultsMissingDisplayNameToEmailLocalPart() {
    CreatedRegistrationInvitation invitation = invitationService.create();

    RegistrationResult result =
        registrationService.register(
            new RegisterRequest(invitation.token(), "Alice", null, PASSWORD));

    assertThat(result.displayName()).isEqualTo("Alice");
    assertThat(mailAccountMapper.selectById(result.mailAccountId()).getDisplayName())
        .isEqualTo("Alice");
  }

  @Test
  void rejectsControlCharactersInDisplayNameWithoutConsumingInvitation() {
    CreatedRegistrationInvitation invitation = invitationService.create();

    assertThatThrownBy(
            () ->
                registrationService.register(
                    new RegisterRequest(invitation.token(), "alice", "Alice\r\nBcc: x", PASSWORD)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

    assertThat(invitationMapper.selectById(invitation.id()).getStatus())
        .isEqualTo(RegistrationInvitationStatus.PENDING);
  }

  @Test
  void rejectsAnEmailAddressInvitationForRegistration() {
    CreatedRegistrationInvitation invitation =
        invitationService.create(RegistrationInvitationPurpose.EMAIL_ADDRESS);

    assertRegistrationError(invitation.token(), "alice", ErrorCode.INVITATION_INVALID);

    RegistrationInvitationEntity pending = invitationMapper.selectById(invitation.id());
    assertThat(pending.getStatus()).isEqualTo(RegistrationInvitationStatus.PENDING);
    assertThat(pending.getPurpose()).isEqualTo(RegistrationInvitationPurpose.EMAIL_ADDRESS);
  }

  @Test
  void keepsSecondInvitationPendingWhenEmailAddressIsTaken() {
    CreatedRegistrationInvitation firstInvitation = invitationService.create();
    CreatedRegistrationInvitation secondInvitation = invitationService.create();
    registrationService.register(
        new RegisterRequest(firstInvitation.token(), "alice", null, PASSWORD));

    assertThatThrownBy(
            () ->
                registrationService.register(
                    new RegisterRequest(secondInvitation.token(), "ALICE", null, PASSWORD)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE));

    assertThat(invitationMapper.selectById(secondInvitation.id()).getStatus())
        .isEqualTo(RegistrationInvitationStatus.PENDING);
    assertThat(appUserMapper.selectCount(null)).isEqualTo(1);
  }

  @Test
  void rejectsReservedAndMalformedEmailNames() {
    CreatedRegistrationInvitation invitation = invitationService.create();

    assertRegistrationError(invitation.token(), "admin", ErrorCode.EMAIL_ADDRESS_NOT_AVAILABLE);
    assertRegistrationError(invitation.token(), "a..b", ErrorCode.BAD_REQUEST);

    assertThat(invitationMapper.selectById(invitation.id()).getStatus())
        .isEqualTo(RegistrationInvitationStatus.PENDING);
  }

  @Test
  void rejectsExpiredAndRevokedInvitations() {
    String expiredToken = "expired-invitation-token-value";
    RegistrationInvitationEntity expired = new RegistrationInvitationEntity();
    expired.setTokenHash(invitationTokenCodec.hash(expiredToken));
    expired.setStatus(RegistrationInvitationStatus.PENDING);
    expired.setPurpose(RegistrationInvitationPurpose.REGISTRATION);
    expired.setExpiresAt(LocalDateTime.ofInstant(clock.instant().minusSeconds(1), clock.getZone()));
    invitationMapper.insert(expired);

    assertRegistrationError(expiredToken, "alice", ErrorCode.INVITATION_EXPIRED);

    CreatedRegistrationInvitation revoked = invitationService.create();
    invitationService.revoke(revoked.id());
    assertRegistrationError(revoked.token(), "bob", ErrorCode.INVITATION_REVOKED);
  }

  private void assertRegistrationError(
      String invitationToken, String localPart, ErrorCode expectedError) {
    assertThatThrownBy(
            () ->
                registrationService.register(
                    new RegisterRequest(invitationToken, localPart, null, PASSWORD)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedError));
  }

  private LocalDateTime applicationNow() {
    return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
  }
}
