package com.yxoct.mail.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  // 通用错误
  BAD_REQUEST(1000, HttpStatus.BAD_REQUEST, "请求参数错误"),

  INTERNAL_ERROR(1001, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),

  RESOURCE_NOT_FOUND(1002, HttpStatus.NOT_FOUND, "请求资源不存在"),

  // 邮件模块错误
  EMAIL_NOT_FOUND(2000, HttpStatus.NOT_FOUND, "邮件不存在"),

  EMAIL_RESTORE_RECORD_NOT_FOUND(2001, HttpStatus.NOT_FOUND, "邮件恢复记录不存在"),

  MAILBOX_NOT_FOUND(2002, HttpStatus.NOT_FOUND, "邮箱不存在"),

  EMAIL_NOT_EXCLUSIVELY_IN_TRASH(2003, HttpStatus.CONFLICT, "邮件并非仅位于回收站"),

  MAIL_SERVICE_UNAVAILABLE(2004, HttpStatus.BAD_GATEWAY, "邮件服务暂时不可用"),

  MAIL_SERVICE_TIMEOUT(2005, HttpStatus.GATEWAY_TIMEOUT, "邮件服务响应超时"),

  MAIL_SERVICE_AUTHENTICATION_FAILED(2006, HttpStatus.BAD_GATEWAY, "邮件服务认证失败"),

  ATTACHMENT_NOT_FOUND(2007, HttpStatus.NOT_FOUND, "附件不存在"),

  // 用户注册错误
  INVITATION_INVALID(3000, HttpStatus.BAD_REQUEST, "邀请码无效"),

  INVITATION_EXPIRED(3001, HttpStatus.GONE, "邀请码已过期"),

  INVITATION_ALREADY_USED(3002, HttpStatus.CONFLICT, "邀请码已被使用"),

  INVITATION_REVOKED(3003, HttpStatus.GONE, "邀请码已被撤销"),

  EMAIL_ADDRESS_NOT_AVAILABLE(3004, HttpStatus.CONFLICT, "邮箱地址不可用"),

  // 身份认证错误
  AUTHENTICATION_FAILED(4000, HttpStatus.UNAUTHORIZED, "邮箱地址或密码错误"),

  ACCOUNT_DISABLED(4001, HttpStatus.FORBIDDEN, "用户账号已被禁用"),

  ACCESS_DENIED(4002, HttpStatus.FORBIDDEN, "无权访问该资源"),

  REFRESH_TOKEN_INVALID(4003, HttpStatus.UNAUTHORIZED, "刷新令牌无效或已过期"),

  MAIL_ACCOUNT_NOT_READY(4004, HttpStatus.CONFLICT, "邮箱账户尚未就绪");

  private final int code;
  private final HttpStatus httpStatus;
  private final String message;

  ErrorCode(int code, HttpStatus httpStatus, String message) {

    this.code = code;
    this.httpStatus = httpStatus;
    this.message = message;
  }
}
