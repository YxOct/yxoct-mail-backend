package com.yxoct.mail.controller;

import com.yxoct.mail.common.response.ApiResponse;
import com.yxoct.mail.domain.user.AdminUserPage;
import com.yxoct.mail.domain.user.AdminUserSummary;
import com.yxoct.mail.domain.user.DisableUserRequest;
import com.yxoct.mail.domain.user.TemporaryPasswordResponse;
import com.yxoct.mail.service.AdminUserService;
import com.yxoct.mail.service.PasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin users", description = "Inspect application users as an administrator")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

  private final AdminUserService userService;
  private final PasswordService passwordService;

  public AdminUserController(AdminUserService userService, PasswordService passwordService) {
    this.userService = userService;
    this.passwordService = passwordService;
  }

  @GetMapping
  @Operation(summary = "List application users")
  public ApiResponse<AdminUserPage> list(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ApiResponse.success(userService.list(page, size));
  }

  @GetMapping("/{userId}")
  @Operation(summary = "Get an application user")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        ref = "#/components/responses/BadRequest"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        ref = "#/components/responses/ResourceNotFound")
  })
  public ApiResponse<AdminUserSummary> get(@PathVariable @Min(1) long userId) {
    return ApiResponse.success(userService.get(userId));
  }

  @PostMapping("/{userId}/disable")
  @Operation(summary = "Disable an application user")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        ref = "#/components/responses/BadRequest"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        ref = "#/components/responses/ResourceNotFound"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        ref = "#/components/responses/UserDisableConflict"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "502",
        ref = "#/components/responses/MailServiceUnavailable")
  })
  public ApiResponse<Void> disable(
      Authentication authentication,
      @PathVariable @Min(1) long userId,
      @Valid @RequestBody DisableUserRequest request) {
    userService.disable(userId(authentication), userId, request.reason());
    return ApiResponse.success();
  }

  @PostMapping("/{userId}/enable")
  @Operation(summary = "Enable a disabled application user")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        ref = "#/components/responses/BadRequest"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        ref = "#/components/responses/ResourceNotFound"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        ref = "#/components/responses/UserEnableConflict"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "502",
        ref = "#/components/responses/MailServiceUnavailable")
  })
  public ApiResponse<Void> enable(
      Authentication authentication, @PathVariable @Min(1) long userId) {
    userService.enable(userId(authentication), userId);
    return ApiResponse.success();
  }

  @PostMapping("/{userId}/logout")
  @Operation(summary = "Revoke every authentication session for an application user")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        ref = "#/components/responses/BadRequest"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        ref = "#/components/responses/ResourceNotFound")
  })
  public ApiResponse<Void> forceLogout(
      Authentication authentication, @PathVariable @Min(1) long userId) {
    userService.forceLogout(userId(authentication), userId);
    return ApiResponse.success();
  }

  @PostMapping("/{userId}/password")
  @Operation(summary = "Reset an application user's password to a temporary password")
  public ApiResponse<TemporaryPasswordResponse> resetPassword(
      Authentication authentication, @PathVariable @Min(1) long userId) {
    return ApiResponse.success(
        passwordService.resetByAdministrator(userId(authentication), userId));
  }

  private long userId(Authentication authentication) {
    Jwt jwt = (Jwt) authentication.getPrincipal();
    return Long.parseLong(jwt.getSubject());
  }
}
