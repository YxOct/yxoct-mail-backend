package com.yxoct.mail.controller;

import com.yxoct.mail.common.response.ApiResponse;
import com.yxoct.mail.domain.mail.AdminMailAccountProvisioningPage;
import com.yxoct.mail.service.AdminMailAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/mail-accounts")
@Tag(name = "Admin mail accounts", description = "Inspect and repair mail account synchronization")
@SecurityRequirement(name = "bearerAuth")
public class AdminMailAccountController {

  private final AdminMailAccountService service;

  public AdminMailAccountController(AdminMailAccountService service) {
    this.service = service;
  }

  @GetMapping("/provisioning")
  @Operation(summary = "List mail accounts awaiting or failing Stalwart provisioning")
  public ApiResponse<AdminMailAccountProvisioningPage> listProvisioningIssues(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ApiResponse.success(service.listProvisioningIssues(page, size));
  }

  @GetMapping("/drifts")
  @Operation(summary = "List detected Stalwart account state drift")
  public ApiResponse<com.yxoct.mail.domain.mail.AdminMailAccountDriftPage> listDrifts(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ApiResponse.success(service.listDrifts(page, size));
  }

  @PostMapping("/{mailAccountId}/retry-provisioning")
  @Operation(summary = "Schedule an immediate retry of Stalwart account provisioning")
  public ApiResponse<Void> retryProvisioning(
      Authentication authentication, @PathVariable @Min(1) long mailAccountId) {
    Jwt jwt = (Jwt) authentication.getPrincipal();
    service.retryProvisioning(
        Long.parseLong(Objects.requireNonNull(jwt.getSubject())), mailAccountId);
    return ApiResponse.success();
  }
}
