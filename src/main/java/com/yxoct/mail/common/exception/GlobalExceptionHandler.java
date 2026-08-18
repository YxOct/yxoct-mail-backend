package com.yxoct.mail.common.exception;

import com.yxoct.mail.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {

    ErrorCode errorCode = exception.getErrorCode();

    ApiResponse<Void> response = ApiResponse.error(errorCode);

    log.warn(
        "Business exception: code={}, status={}, message={}",
        errorCode.getCode(),
        errorCode.getHttpStatus(),
        errorCode.getMessage(),
        exception.getCause());

    return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
      MethodArgumentNotValidException exception) {

    log.warn("Request validation failed: {}", exception.getMessage());

    return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus())
        .body(ApiResponse.error(ErrorCode.BAD_REQUEST));
  }

  @ExceptionHandler({
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleRequestParameterException(Exception exception) {

    log.warn("Request parameter validation failed: {}", exception.getMessage());

    return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus())
        .body(ApiResponse.error(ErrorCode.BAD_REQUEST));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
      NoResourceFoundException exception) {
    log.warn("Request resource not found: {}", exception.getResourcePath());
    return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
        .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {

    log.error("Unhandled exception", exception);
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
        .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
  }
}
