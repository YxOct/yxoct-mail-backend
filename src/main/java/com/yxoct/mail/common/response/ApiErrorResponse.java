package com.yxoct.mail.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiErrorResponse", description = "Common top-level API error response")
public record ApiErrorResponse(
    @Schema(description = "Application error code", example = "1000") int code,
    @Schema(description = "Human-readable error message", example = "请求参数错误") String message,
    @Schema(description = "Always null for a top-level error response") Object data) {}
