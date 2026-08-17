package com.yxoct.mail.common.response;

import com.yxoct.mail.common.exception.ErrorCode;

public record ApiResponse<T>(int code, String message, T data) {
  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(0, "success", data);
  }

  public static ApiResponse<Void> success() {
    return new ApiResponse<>(0, "success", null);
  }

  public static <T> ApiResponse<T> error(ErrorCode errorCode) {
    return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
  }
}
