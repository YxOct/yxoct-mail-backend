package com.yxoct.mail.client.stalwart;

public class StalwartProvisioningException extends RuntimeException {

  private final String failureCode;

  public StalwartProvisioningException(String failureCode) {
    super(failureCode);
    this.failureCode = failureCode;
  }

  public StalwartProvisioningException(String failureCode, Throwable cause) {
    super(failureCode, cause);
    this.failureCode = failureCode;
  }

  public String failureCode() {
    return failureCode;
  }
}
