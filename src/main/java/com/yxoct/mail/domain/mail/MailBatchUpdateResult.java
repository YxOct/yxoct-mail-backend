package com.yxoct.mail.domain.mail;

import java.util.List;

public record MailBatchUpdateResult(List<String> updatedIds, List<Failure> failed) {

  public record Failure(String id, int code, String message) {}
}
