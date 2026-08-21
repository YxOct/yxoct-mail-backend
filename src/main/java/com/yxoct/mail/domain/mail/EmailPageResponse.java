package com.yxoct.mail.domain.mail;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Concrete OpenAPI model for the email-summary page returned at runtime. */
@Schema(description = "One-based page of email summaries")
public record EmailPageResponse(int page, int size, int total, List<MailSummary> items) {}
