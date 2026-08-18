package com.yxoct.mail.controller;

import com.yxoct.mail.common.response.ApiResponse;
import com.yxoct.mail.domain.user.CreateInvitationRequest;
import com.yxoct.mail.domain.user.CreatedRegistrationInvitation;
import com.yxoct.mail.domain.user.RegistrationInvitationSummary;
import com.yxoct.mail.service.RegistrationInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/invitations")
@Tag(name = "Admin invitations", description = "Manage invitation tokens as an administrator")
@SecurityRequirement(name = "bearerAuth")
public class AdminInvitationController {

  private final RegistrationInvitationService invitationService;

  public AdminInvitationController(RegistrationInvitationService invitationService) {
    this.invitationService = invitationService;
  }

  @PostMapping
  @Operation(summary = "Create a single-use invitation")
  public ResponseEntity<ApiResponse<CreatedRegistrationInvitation>> create(
      Authentication authentication, @Valid @RequestBody CreateInvitationRequest request) {
    CreatedRegistrationInvitation invitation =
        invitationService.create(request.purpose(), userId(authentication));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(invitation));
  }

  @GetMapping
  @Operation(summary = "List the most recent invitations")
  public ApiResponse<List<RegistrationInvitationSummary>> list(
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
    return ApiResponse.success(invitationService.list(limit));
  }

  @DeleteMapping("/{invitationId}")
  @Operation(summary = "Revoke a pending invitation")
  public ApiResponse<Void> revoke(
      Authentication authentication, @PathVariable @Min(1) long invitationId) {
    invitationService.revoke(invitationId, userId(authentication));
    return ApiResponse.success();
  }

  private long userId(Authentication authentication) {
    Jwt jwt = (Jwt) authentication.getPrincipal();
    return Long.parseLong(jwt.getSubject());
  }
}
