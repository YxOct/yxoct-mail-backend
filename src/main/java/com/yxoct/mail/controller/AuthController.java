package com.yxoct.mail.controller;

import com.yxoct.mail.common.response.ApiResponse;
import com.yxoct.mail.domain.user.AccessTokenResponse;
import com.yxoct.mail.domain.user.LoginRequest;
import com.yxoct.mail.domain.user.RegisterRequest;
import com.yxoct.mail.domain.user.RegistrationResult;
import com.yxoct.mail.service.LoginService;
import com.yxoct.mail.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  public AuthController(RegistrationService registrationService, LoginService loginService) {
    this.registrationService = registrationService;
    this.loginService = loginService;
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
  public ApiResponse<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.success(loginService.login(request));
  }
}
