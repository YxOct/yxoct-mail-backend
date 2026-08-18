package com.yxoct.mail.controller;

import com.yxoct.mail.common.response.ApiResponse;
import com.yxoct.mail.domain.mail.BatchEmailIdsRequest;
import com.yxoct.mail.domain.mail.BatchUpdateReadStatusRequest;
import com.yxoct.mail.domain.mail.BatchUpdateStarStatusRequest;
import com.yxoct.mail.domain.mail.MailBatchUpdateResult;
import com.yxoct.mail.domain.mail.MailDetail;
import com.yxoct.mail.domain.mail.MailPage;
import com.yxoct.mail.domain.mail.MailSummary;
import com.yxoct.mail.domain.mail.Mailbox;
import com.yxoct.mail.domain.mail.UpdateReadStatusRequest;
import com.yxoct.mail.domain.mail.UpdateStarStatusRequest;
import com.yxoct.mail.service.MailService;
import com.yxoct.mail.service.MailTrashService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mail")
public class MailController {

  private final MailService mailService;
  private final MailTrashService mailTrashService;

  public MailController(MailService mailService, MailTrashService mailTrashService) {
    this.mailService = mailService;
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
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

    return ApiResponse.success(mailService.queryEmails(mailboxId, page, size));
  }

  /** 获取邮件详情 */
  @GetMapping("/emails/{id}")
  public ApiResponse<MailDetail> detail(@PathVariable String id) {

    return ApiResponse.success(mailService.getEmailDetail(id));
  }

  /** 更新邮件已读状态 */
  @PatchMapping("/emails/{id}/read-status")
  public ApiResponse<Void> updateReadStatus(
      @PathVariable String id, @Valid @RequestBody UpdateReadStatusRequest request) {

    mailService.updateReadStatus(id, request.read());
    return ApiResponse.success();
  }

  /** 批量更新邮件已读状态 */
  @PatchMapping("/emails/read-status")
  public ApiResponse<MailBatchUpdateResult> updateReadStatuses(
      @Valid @RequestBody BatchUpdateReadStatusRequest request) {

    return ApiResponse.success(mailService.updateReadStatuses(request.ids(), request.read()));
  }

  /** 更新邮件星标状态 */
  @PatchMapping("/emails/{id}/star-status")
  public ApiResponse<Void> updateStarStatus(
      @PathVariable String id, @Valid @RequestBody UpdateStarStatusRequest request) {

    mailService.updateStarStatus(id, request.starred());
    return ApiResponse.success();
  }

  /** 批量更新邮件星标状态 */
  @PatchMapping("/emails/star-status")
  public ApiResponse<MailBatchUpdateResult> updateStarStatuses(
      @Valid @RequestBody BatchUpdateStarStatusRequest request) {

    return ApiResponse.success(mailService.updateStarStatuses(request.ids(), request.starred()));
  }

  /** 将邮件移入垃圾箱 */
  @PostMapping("/emails/{id}/trash")
  public ApiResponse<Void> moveToTrash(@PathVariable String id) {
    mailTrashService.moveEmailToTrash(id);
    return ApiResponse.success();
  }

  /** 批量将邮件移入垃圾箱 */
  @PostMapping("/emails/trash")
  public ApiResponse<MailBatchUpdateResult> moveToTrash(
      @Valid @RequestBody BatchEmailIdsRequest request) {
    return ApiResponse.success(mailTrashService.moveEmailsToTrash(request.ids()));
  }

  /** 将邮件恢复到删除前的邮箱 */
  @PostMapping("/emails/{id}/restore")
  public ApiResponse<Void> restore(@PathVariable String id) {
    mailTrashService.restoreEmail(id);
    return ApiResponse.success();
  }

  /** 批量将邮件恢复到删除前的邮箱 */
  @PostMapping("/emails/restore")
  public ApiResponse<MailBatchUpdateResult> restore(
      @Valid @RequestBody BatchEmailIdsRequest request) {
    return ApiResponse.success(mailTrashService.restoreEmails(request.ids()));
  }
}
