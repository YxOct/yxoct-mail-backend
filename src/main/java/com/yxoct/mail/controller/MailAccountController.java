package com.yxoct.mail.controller;

import com.yxoct.mail.common.response.ApiResponse;
import com.yxoct.mail.domain.mail.CreateEmailAliasRequest;
import com.yxoct.mail.domain.mail.EmailAliasResult;
import com.yxoct.mail.domain.mail.MailAccountSettings;
import com.yxoct.mail.domain.mail.UpdateMailAccountRequest;
import com.yxoct.mail.service.EmailAliasService;
import com.yxoct.mail.service.MailAccountSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/mail/accounts")
@Tag(name = "Mail accounts", description = "Manage authenticated users' mail accounts")
@SecurityRequirement(name = "bearerAuth")
public class MailAccountController {

  private final MailAccountSettingsService settingsService;
  private final EmailAliasService aliasService;

  public MailAccountController(
      MailAccountSettingsService settingsService, EmailAliasService aliasService) {
    this.settingsService = settingsService;
    this.aliasService = aliasService;
  }

  @PostMapping("/{mailAccountId}/aliases")
  @Operation(summary = "Add an invitation-authorized alias to an owned mail account")
  public ApiResponse<EmailAliasResult> createAlias(
      Authentication authentication,
      @PathVariable @Min(1) long mailAccountId,
      @Valid @RequestBody CreateEmailAliasRequest request) {
    Jwt jwt = (Jwt) authentication.getPrincipal();
    return ApiResponse.success(
        aliasService.create(
            jwt.getSubject(), mailAccountId, request.invitationCode(), request.emailLocalPart()));
  }

  @PatchMapping("/{mailAccountId}")
  @Operation(summary = "Update an owned mail account's display name")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        ref = "#/components/responses/BadRequest"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        ref = "#/components/responses/ResourceNotFound"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        ref = "#/components/responses/MailAccountNotReady"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "502",
        ref = "#/components/responses/MailServiceUnavailable")
  })
  public ApiResponse<MailAccountSettings> update(
      Authentication authentication,
      @PathVariable @Min(1) long mailAccountId,
      @Valid @RequestBody UpdateMailAccountRequest request) {
    Jwt jwt = (Jwt) authentication.getPrincipal();
    return ApiResponse.success(
        settingsService.updateDisplayName(jwt.getSubject(), mailAccountId, request.displayName()));
  }
}
