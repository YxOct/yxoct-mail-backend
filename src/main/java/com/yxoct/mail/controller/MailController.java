package com.yxoct.mail.controller;

import com.yxoct.mail.common.response.ApiResponse;
import com.yxoct.mail.domain.mail.BatchEmailIdsRequest;
import com.yxoct.mail.domain.mail.BatchUpdateReadStatusRequest;
import com.yxoct.mail.domain.mail.BatchUpdateStarStatusRequest;
import com.yxoct.mail.domain.mail.MailBatchUpdateResult;
import com.yxoct.mail.domain.mail.MailDetail;
import com.yxoct.mail.domain.mail.MailOpenApiResponses;
import com.yxoct.mail.domain.mail.MailPage;
import com.yxoct.mail.domain.mail.MailQueryFilter;
import com.yxoct.mail.domain.mail.MailSort;
import com.yxoct.mail.domain.mail.MailSummary;
import com.yxoct.mail.domain.mail.Mailbox;
import com.yxoct.mail.domain.mail.MoveEmailsRequest;
import com.yxoct.mail.service.MailMoveService;
import com.yxoct.mail.service.MailService;
import com.yxoct.mail.service.MailTrashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/mail")
@Tag(name = "Mail", description = "Receive and manage mail through Stalwart JMAP")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "500",
      ref = "#/components/responses/InternalError"),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "502",
      ref = "#/components/responses/MailServiceUnavailable"),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "504",
      ref = "#/components/responses/MailServiceTimeout")
})
public class MailController {

  private final MailService mailService;
  private final MailMoveService mailMoveService;
  private final MailTrashService mailTrashService;

  public MailController(
      MailService mailService, MailMoveService mailMoveService, MailTrashService mailTrashService) {
    this.mailService = mailService;
    this.mailMoveService = mailMoveService;
    this.mailTrashService = mailTrashService;
  }

  /** 获取邮箱列表 */
  @GetMapping("/mailboxes")
  @Operation(summary = "List mailboxes")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Mailbox list",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = MailOpenApiResponses.MailboxList.class)))
  public ApiResponse<List<Mailbox>> mailboxes() {

    return ApiResponse.success(mailService.getMailboxes());
  }

  /** 分页查询指定邮箱邮件 */
  @GetMapping("/mailboxes/{mailboxId}/emails")
  @Operation(
      summary = "List emails in a mailbox",
      description = "Supports search, status filters, sorting, and pagination.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "One-based page of email summaries",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = MailOpenApiResponses.EmailPage.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      ref = "#/components/responses/BadRequest")
  public ApiResponse<MailPage<MailSummary>> emails(
      @Parameter(description = "JMAP mailbox ID", required = true) @PathVariable String mailboxId,
      @Parameter(description = "One-based page number", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(1)
          int page,
      @Parameter(description = "Page size between 1 and 100", example = "20")
          @RequestParam(defaultValue = "20")
          @Min(1)
          @Max(100)
          int size,
      @Parameter(description = "Full-text search keyword, up to 200 characters")
          @RequestParam(required = false)
          @Size(max = 200)
          String keyword,
      @Parameter(description = "Filter by read status") @RequestParam(required = false)
          Boolean read,
      @Parameter(description = "Filter by starred status") @RequestParam(required = false)
          Boolean starred,
      @Parameter(
              description = "Sort field: receivedAt, sentAt, subject, from, to, or size",
              example = "receivedAt",
              schema =
                  @Schema(
                      allowableValues = {"receivedAt", "sentAt", "subject", "from", "to", "size"}))
          @RequestParam(defaultValue = "receivedAt")
          String sortBy,
      @Parameter(
              description = "Sort direction: asc or desc",
              example = "desc",
              schema = @Schema(allowableValues = {"asc", "desc"}))
          @RequestParam(defaultValue = "desc")
          String direction) {

    return ApiResponse.success(
        mailService.queryEmails(
            mailboxId,
            page,
            size,
            new MailQueryFilter(keyword, read, starred),
            MailSort.parse(sortBy, direction)));
  }

  /** 获取邮件详情 */
  @GetMapping("/emails/{id}")
  @Operation(summary = "Get an email detail")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Email detail",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = MailOpenApiResponses.EmailDetail.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      ref = "#/components/responses/EmailNotFound")
  public ApiResponse<MailDetail> detail(@PathVariable String id) {

    return ApiResponse.success(mailService.getEmailDetail(id));
  }

  /** 下载邮件附件 */
  @GetMapping("/emails/{emailId}/attachments/{blobId}")
  @Operation(summary = "Download an email attachment")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Attachment binary stream",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
              schema = @Schema(type = "string", format = "binary")))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      ref = "#/components/responses/BadRequest")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      ref = "#/components/responses/EmailOrAttachmentNotFound")
  public ResponseEntity<StreamingResponseBody> downloadAttachment(
      @PathVariable String emailId, @PathVariable String blobId) {
    var attachment = mailService.getAttachment(emailId, blobId);
    String filename =
        attachment.name() == null || attachment.name().isBlank() ? "attachment" : attachment.name();
    ContentDisposition.Builder dispositionBuilder =
        attachment.inline() ? ContentDisposition.inline() : ContentDisposition.attachment();
    ContentDisposition disposition =
        dispositionBuilder.filename(filename, StandardCharsets.UTF_8).build();
    StreamingResponseBody body =
        outputStream -> mailService.downloadAttachment(attachment, outputStream);

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(attachment.type()))
        .contentLength(attachment.size())
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .body(body);
  }

  /** 批量更新邮件已读状态 */
  @PatchMapping("/emails/read-status")
  @Operation(summary = "Update read status for up to 100 emails")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description =
          "Per-email batch result; updatedIds succeeded and failed contains individual failures",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = MailOpenApiResponses.BatchUpdate.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      ref = "#/components/responses/BadRequest")
  public ApiResponse<MailBatchUpdateResult> updateReadStatuses(
      @Valid @RequestBody BatchUpdateReadStatusRequest request) {

    return ApiResponse.success(mailService.updateReadStatuses(request.ids(), request.read()));
  }

  /** 批量更新邮件星标状态 */
  @PatchMapping("/emails/star-status")
  @Operation(summary = "Update starred status for up to 100 emails")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description =
          "Per-email batch result; updatedIds succeeded and failed contains individual failures",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = MailOpenApiResponses.BatchUpdate.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      ref = "#/components/responses/BadRequest")
  public ApiResponse<MailBatchUpdateResult> updateStarStatuses(
      @Valid @RequestBody BatchUpdateStarStatusRequest request) {

    return ApiResponse.success(mailService.updateStarStatuses(request.ids(), request.starred()));
  }

  /** 批量移动邮件到指定邮箱 */
  @PostMapping("/emails/move")
  @Operation(summary = "Move up to 100 emails to a mailbox")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description =
          "Per-email batch result; updatedIds succeeded and failed contains individual failures",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = MailOpenApiResponses.BatchUpdate.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      ref = "#/components/responses/BadRequest")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      ref = "#/components/responses/MailboxNotFound")
  public ApiResponse<MailBatchUpdateResult> move(@Valid @RequestBody MoveEmailsRequest request) {
    return ApiResponse.success(
        mailMoveService.moveEmails(request.ids(), request.targetMailboxId()));
  }

  /** 批量将邮件移入垃圾箱 */
  @PostMapping("/emails/trash")
  @Operation(summary = "Move up to 100 emails to Trash")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description =
          "Per-email batch result; updatedIds succeeded and failed contains individual failures",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = MailOpenApiResponses.BatchUpdate.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      ref = "#/components/responses/BadRequest")
  public ApiResponse<MailBatchUpdateResult> moveToTrash(
      @Valid @RequestBody BatchEmailIdsRequest request) {
    return ApiResponse.success(mailTrashService.moveEmailsToTrash(request.ids()));
  }

  /** 批量将邮件恢复到删除前的邮箱 */
  @PostMapping("/emails/restore")
  @Operation(summary = "Restore up to 100 emails from Trash")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description =
          "Per-email batch result; updatedIds succeeded and failed contains individual failures",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = MailOpenApiResponses.BatchUpdate.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      ref = "#/components/responses/BadRequest")
  public ApiResponse<MailBatchUpdateResult> restore(
      @Valid @RequestBody BatchEmailIdsRequest request) {
    return ApiResponse.success(mailTrashService.restoreEmails(request.ids()));
  }

  /** 批量永久删除回收站中的邮件 */
  @DeleteMapping("/emails")
  @Operation(summary = "Permanently delete up to 100 emails from Trash")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description =
          "Per-email batch result; updatedIds succeeded and failed contains individual failures",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = MailOpenApiResponses.BatchUpdate.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      ref = "#/components/responses/BadRequest")
  public ApiResponse<MailBatchUpdateResult> permanentlyDelete(
      @Valid @RequestBody BatchEmailIdsRequest request) {
    return ApiResponse.success(mailTrashService.permanentlyDeleteEmails(request.ids()));
  }
}
