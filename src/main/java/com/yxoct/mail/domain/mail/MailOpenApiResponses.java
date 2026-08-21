package com.yxoct.mail.domain.mail;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Concrete response envelopes used to preserve generic response data in OpenAPI. */
public final class MailOpenApiResponses {

  private MailOpenApiResponses() {}

  @Schema(name = "MailboxListResponse", description = "Successful mailbox list response")
  public record MailboxList(int code, String message, List<Mailbox> data) {}

  @Schema(name = "EmailPageApiResponse", description = "Successful email page response")
  public record EmailPage(int code, String message, EmailPageResponse data) {}

  @Schema(name = "EmailDetailApiResponse", description = "Successful email detail response")
  public record EmailDetail(int code, String message, MailDetail data) {}

  @Schema(
      name = "MailBatchUpdateResponse",
      description = "Successful, possibly partial, batch operation response")
  public record BatchUpdate(int code, String message, MailBatchUpdateResult data) {}
}
