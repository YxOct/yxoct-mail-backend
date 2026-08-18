package com.yxoct.mail.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  // 通用错误
  BAD_REQUEST(1000, HttpStatus.BAD_REQUEST, "请求参数错误"),

  INTERNAL_ERROR(1001, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),

  // 邮件模块错误
  EMAIL_NOT_FOUND(2000, HttpStatus.NOT_FOUND, "邮件不存在"),

  EMAIL_RESTORE_RECORD_NOT_FOUND(2001, HttpStatus.NOT_FOUND, "邮件恢复记录不存在"),

  MAILBOX_NOT_FOUND(2002, HttpStatus.NOT_FOUND, "邮箱不存在"),

  EMAIL_NOT_EXCLUSIVELY_IN_TRASH(2003, HttpStatus.CONFLICT, "邮件并非仅位于回收站"),

  MAIL_SERVICE_UNAVAILABLE(2004, HttpStatus.BAD_GATEWAY, "邮件服务暂时不可用"),

  MAIL_SERVICE_TIMEOUT(2005, HttpStatus.GATEWAY_TIMEOUT, "邮件服务响应超时"),

  MAIL_SERVICE_AUTHENTICATION_FAILED(2006, HttpStatus.BAD_GATEWAY, "邮件服务认证失败");

  private final int code;
  private final HttpStatus httpStatus;
  private final String message;

  ErrorCode(int code, HttpStatus httpStatus, String message) {

    this.code = code;
    this.httpStatus = httpStatus;
    this.message = message;
  }
}
