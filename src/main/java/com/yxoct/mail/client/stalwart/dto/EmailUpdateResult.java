package com.yxoct.mail.client.stalwart.dto;

import java.util.List;

public record EmailUpdateResult(List<String> updatedIds, List<Failure> failures) {

  public record Failure(String id, String type) {}
}
