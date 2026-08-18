package com.yxoct.mail.controller;

import com.yxoct.mail.common.response.ApiResponse;
import com.yxoct.mail.domain.mail.BatchEmailIdsRequest;
import com.yxoct.mail.domain.mail.BatchUpdateReadStatusRequest;
import com.yxoct.mail.domain.mail.BatchUpdateStarStatusRequest;
import com.yxoct.mail.domain.mail.MailBatchUpdateResult;
import com.yxoct.mail.domain.mail.MailDetail;
import com.yxoct.mail.domain.mail.MailPage;
import com.yxoct.mail.domain.mail.MailQueryFilter;
import com.yxoct.mail.domain.mail.MailSort;
import com.yxoct.mail.domain.mail.MailSummary;
import com.yxoct.mail.domain.mail.Mailbox;
import com.yxoct.mail.domain.mail.MoveEmailsRequest;
import com.yxoct.mail.service.MailMoveService;
import com.yxoct.mail.service.MailService;
import com.yxoct.mail.service.MailTrashService;
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
  public ApiResponse<List<Mailbox>> mailboxes() {

    return ApiResponse.success(mailService.getMailboxes());
  }

  /** 分页查询指定邮箱邮件 */
  @GetMapping("/mailboxes/{mailboxId}/emails")
  public ApiResponse<MailPage<MailSummary>> emails(
      @PathVariable String mailboxId,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) @Size(max = 200) String keyword,
      @RequestParam(required = false) Boolean read,
      @RequestParam(required = false) Boolean starred,
      @RequestParam(defaultValue = "receivedAt") String sortBy,
      @RequestParam(defaultValue = "desc") String direction) {

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
  public ApiResponse<MailDetail> detail(@PathVariable String id) {

    return ApiResponse.success(mailService.getEmailDetail(id));
  }

  /** 下载邮件附件 */
  @GetMapping("/emails/{emailId}/attachments/{blobId}")
  public ResponseEntity<StreamingResponseBody> downloadAttachment(
      @PathVariable String emailId, @PathVariable String blobId) {
    var attachment = mailService.getAttachment(emailId, blobId);
    String filename =
        attachment.name() == null || attachment.name().isBlank() ? "attachment" : attachment.name();
    ContentDisposition disposition =
        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build();
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
  public ApiResponse<MailBatchUpdateResult> updateReadStatuses(
      @Valid @RequestBody BatchUpdateReadStatusRequest request) {

    return ApiResponse.success(mailService.updateReadStatuses(request.ids(), request.read()));
  }

  /** 批量更新邮件星标状态 */
  @PatchMapping("/emails/star-status")
  public ApiResponse<MailBatchUpdateResult> updateStarStatuses(
      @Valid @RequestBody BatchUpdateStarStatusRequest request) {

    return ApiResponse.success(mailService.updateStarStatuses(request.ids(), request.starred()));
  }

  /** 批量移动邮件到指定邮箱 */
  @PostMapping("/emails/move")
  public ApiResponse<MailBatchUpdateResult> move(@Valid @RequestBody MoveEmailsRequest request) {
    return ApiResponse.success(
        mailMoveService.moveEmails(request.ids(), request.targetMailboxId()));
  }

  /** 批量将邮件移入垃圾箱 */
  @PostMapping("/emails/trash")
  public ApiResponse<MailBatchUpdateResult> moveToTrash(
      @Valid @RequestBody BatchEmailIdsRequest request) {
    return ApiResponse.success(mailTrashService.moveEmailsToTrash(request.ids()));
  }

  /** 批量将邮件恢复到删除前的邮箱 */
  @PostMapping("/emails/restore")
  public ApiResponse<MailBatchUpdateResult> restore(
      @Valid @RequestBody BatchEmailIdsRequest request) {
    return ApiResponse.success(mailTrashService.restoreEmails(request.ids()));
  }

  /** 批量永久删除回收站中的邮件 */
  @DeleteMapping("/emails")
  public ApiResponse<MailBatchUpdateResult> permanentlyDelete(
      @Valid @RequestBody BatchEmailIdsRequest request) {
    return ApiResponse.success(mailTrashService.permanentlyDeleteEmails(request.ids()));
  }
}
