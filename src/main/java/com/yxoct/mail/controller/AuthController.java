package com.yxoct.mail.controller;

import com.yxoct.mail.common.response.ApiResponse;
import com.yxoct.mail.domain.user.ChangePasswordRequest;
import com.yxoct.mail.domain.user.CurrentUserResponse;
import com.yxoct.mail.domain.user.LoginRequest;
import com.yxoct.mail.domain.user.RefreshTokenRequest;
import com.yxoct.mail.domain.user.RegisterRequest;
import com.yxoct.mail.domain.user.RegistrationResult;
import com.yxoct.mail.domain.user.TokenPairResponse;
import com.yxoct.mail.service.CurrentUserService;
import com.yxoct.mail.service.LoginService;
import com.yxoct.mail.service.PasswordService;
import com.yxoct.mail.service.RefreshTokenService;
import com.yxoct.mail.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Register and authenticate application users")
public class AuthController {

  private final RegistrationService registrationService;
  private final LoginService loginService;
  private final RefreshTokenService refreshTokenService;
  private final CurrentUserService currentUserService;
  private final PasswordService passwordService;

  public AuthController(
      RegistrationService registrationService,
      LoginService loginService,
      RefreshTokenService refreshTokenService,
      CurrentUserService currentUserService,
      PasswordService passwordService) {
    this.registrationService = registrationService;
    this.loginService = loginService;
    this.refreshTokenService = refreshTokenService;
    this.currentUserService = currentUserService;
    this.passwordService = passwordService;
  }

  @GetMapping("/me")
  @Operation(summary = "Get the authenticated user and primary mail account status")
  @SecurityRequirement(name = "bearerAuth")
  public ApiResponse<CurrentUserResponse> me(Authentication authentication) {
    Jwt jwt = (Jwt) authentication.getPrincipal();
    return ApiResponse.success(currentUserService.get(jwt.getSubject()));
  }

  @PostMapping("/password")
  @Operation(summary = "Change the authenticated user's password")
  @SecurityRequirement(name = "bearerAuth")
  public ApiResponse<Void> changePassword(
      Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
    Jwt jwt = (Jwt) authentication.getPrincipal();
    passwordService.change(
        Long.parseLong(jwt.getSubject()), request.currentPassword(), request.newPassword());
    return ApiResponse.success();
  }

  @PostMapping("/register")
  @Operation(
      summary = "Register with an invitation",
      description = "Creates a local user and a provisioning mail account.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        ref = "#/components/responses/InvalidInvitationOrRequest"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        ref = "#/components/responses/RegistrationConflict"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "410",
        ref = "#/components/responses/InvitationGone"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "500",
        ref = "#/components/responses/InternalError")
  })
  public ResponseEntity<ApiResponse<RegistrationResult>> register(
      @Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(registrationService.register(request)));
  }

  @PostMapping("/login")
  @Operation(summary = "Log in with the primary email address")
  public ApiResponse<TokenPairResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    return ApiResponse.success(loginService.login(request, clientIp(httpRequest)));
  }

  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",", 2)[0].trim();
    }
    return request.getRemoteAddr();
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rotate a refresh token and issue a new token pair")
  public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return ApiResponse.success(refreshTokenService.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  @Operation(summary = "Revoke a refresh token")
  public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
    refreshTokenService.revoke(request.refreshToken());
    return ApiResponse.success();
  }
}
