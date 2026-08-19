package com.yxoct.mail.controller;

import com.yxoct.mail.common.response.ApiResponse;
import com.yxoct.mail.domain.user.AdminUserPage;
import com.yxoct.mail.domain.user.AdminUserSummary;
import com.yxoct.mail.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  public AdminUserController(AdminUserService userService) {
    this.userService = userService;
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
}
