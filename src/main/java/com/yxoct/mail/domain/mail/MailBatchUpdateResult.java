package com.yxoct.mail.domain.mail;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Result of a batch email operation that may partially succeed")
public record MailBatchUpdateResult(
    @Schema(description = "IDs updated successfully") List<String> updatedIds,
    @Schema(description = "Per-email failures; the overall HTTP response can still be successful")
        List<Failure> failed) {

  @Schema(description = "Failure for one email in a batch operation")
  public record Failure(
      @Schema(description = "Email ID") String id,
      @Schema(
              description = "Application error code, such as 2000, 2001, 2003, or 2004",
              example = "2000")
          int code,
      @Schema(description = "Human-readable error message") String message) {}
}
