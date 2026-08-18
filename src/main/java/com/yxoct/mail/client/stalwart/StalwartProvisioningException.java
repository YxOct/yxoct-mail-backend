package com.yxoct.mail.client.stalwart;

public class StalwartProvisioningException extends RuntimeException {

  private final String failureCode;
  private final String diagnostic;

  public StalwartProvisioningException(String failureCode) {
    super(failureCode);
    this.failureCode = failureCode;
    this.diagnostic = null;
  }

  public StalwartProvisioningException(String failureCode, String diagnostic) {
    super(failureCode);
    this.failureCode = failureCode;
    this.diagnostic = diagnostic;
  }

  public StalwartProvisioningException(String failureCode, Throwable cause) {
    super(failureCode, cause);
    this.failureCode = failureCode;
    this.diagnostic = null;
  }

  public String failureCode() {
    return failureCode;
  }

  public String diagnostic() {
    return diagnostic;
  }
}
