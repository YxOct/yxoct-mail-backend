package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.JmapClient;
import com.yxoct.mail.client.stalwart.JmapSessionCache;
import com.yxoct.mail.client.stalwart.dto.EmailMailboxResult;
import com.yxoct.mail.client.stalwart.dto.EmailUpdateResult;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.client.stalwart.dto.MailboxGetResult;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.mail.MailBatchUpdateResult;
import com.yxoct.mail.persistence.EmailRestoreRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MailTrashService {

  private final JmapClient jmapClient;
  private final JmapSessionCache sessionCache;
  private final EmailRestoreRepository restoreRepository;

  public MailTrashService(
      JmapClient jmapClient,
      JmapSessionCache sessionCache,
      EmailRestoreRepository restoreRepository) {
    this.jmapClient = jmapClient;
    this.sessionCache = sessionCache;
    this.restoreRepository = restoreRepository;
  }

  public MailBatchUpdateResult moveEmailsToTrash(List<String> ids) {
    validateIds(ids);
    List<String> requestedIds = List.copyOf(ids);
    JmapSession session = sessionCache.getSession();
    String accountId = requireMailAccountId(session);
    MailboxContext mailboxes = getMailboxContext(session);
    String trashId = mailboxes.requireRole("trash");
    EmailMailboxResult locations = jmapClient.getEmailMailboxes(session, requestedIds);

    Map<String, EmailMailboxResult.EmailInfo> locationsById =
        locations.list().stream()
            .collect(
                Collectors.toMap(
                    EmailMailboxResult.EmailInfo::id,
                    email -> email,
                    (left, right) -> left,
                    LinkedHashMap::new));
    Map<String, ErrorCode> failures = new LinkedHashMap<>();
    locations.notFound().forEach(id -> failures.put(id, ErrorCode.EMAIL_NOT_FOUND));
    Map<String, List<String>> updates = new LinkedHashMap<>();
    Set<String> newlyStoredIds = new HashSet<>();

    for (String id : requestedIds) {
      EmailMailboxResult.EmailInfo location = locationsById.get(id);
      if (location == null) {
        continue;
      }

      Optional<List<String>> existing = restoreRepository.findMailboxIds(accountId, id);
      if (existing.isEmpty()) {
        List<String> originalMailboxIds =
            location.mailboxIds().keySet().stream().filter(Predicate.not(trashId::equals)).toList();
        if (originalMailboxIds.isEmpty()) {
          originalMailboxIds = List.of(mailboxes.requireRole("inbox"));
        }
        if (restoreRepository.saveIfAbsent(accountId, id, originalMailboxIds)) {
          newlyStoredIds.add(id);
        }
      }
      updates.put(id, List.of(trashId));
    }

    if (updates.isEmpty()) {
      return batchResult(requestedIds, List.of(), failures);
    }

    EmailUpdateResult updateResult = jmapClient.setEmailMailboxes(session, updates);
    updateResult
        .failures()
        .forEach(failure -> failures.put(failure.id(), updateErrorCode(failure.type())));
    Set<String> failedUpdateIds =
        updateResult.failures().stream()
            .map(EmailUpdateResult.Failure::id)
            .collect(Collectors.toSet());
    cleanupRestoreRecords(accountId, intersection(newlyStoredIds, failedUpdateIds));
    return batchResult(requestedIds, updateResult.updatedIds(), failures);
  }

  public MailBatchUpdateResult restoreEmails(List<String> ids) {
    validateIds(ids);
    List<String> requestedIds = List.copyOf(ids);
    JmapSession session = sessionCache.getSession();
    String accountId = requireMailAccountId(session);
    Map<String, ErrorCode> failures = new LinkedHashMap<>();
    Map<String, List<String>> storedLocations = new LinkedHashMap<>();

    for (String id : requestedIds) {
      Optional<List<String>> storedMailboxIds = restoreRepository.findMailboxIds(accountId, id);
      if (storedMailboxIds.isEmpty()) {
        failures.put(id, ErrorCode.EMAIL_RESTORE_RECORD_NOT_FOUND);
        continue;
      }
      storedLocations.put(id, storedMailboxIds.get());
    }

    if (storedLocations.isEmpty()) {
      return batchResult(requestedIds, List.of(), failures);
    }

    MailboxContext mailboxes = getMailboxContext(session);
    Map<String, List<String>> updates = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> storedLocation : storedLocations.entrySet()) {
      List<String> targetMailboxIds =
          storedLocation.getValue().stream().filter(mailboxes.availableIds()::contains).toList();
      if (targetMailboxIds.isEmpty()) {
        targetMailboxIds = List.of(mailboxes.requireRole("inbox"));
      }
      updates.put(storedLocation.getKey(), targetMailboxIds);
    }

    EmailUpdateResult updateResult = jmapClient.setEmailMailboxes(session, updates);
    updateResult
        .failures()
        .forEach(failure -> failures.put(failure.id(), updateErrorCode(failure.type())));
    restoreRepository.deleteAll(accountId, updateResult.updatedIds());
    return batchResult(requestedIds, updateResult.updatedIds(), failures);
  }

  public MailBatchUpdateResult permanentlyDeleteEmails(List<String> ids) {
    validateIds(ids);
    List<String> requestedIds = List.copyOf(ids);
    JmapSession session = sessionCache.getSession();
    String accountId = requireMailAccountId(session);
    String trashId = getMailboxContext(session).requireRole("trash");
    EmailMailboxResult locations = jmapClient.getEmailMailboxes(session, requestedIds);
    Map<String, EmailMailboxResult.EmailInfo> locationsById =
        locations.list().stream()
            .collect(Collectors.toMap(EmailMailboxResult.EmailInfo::id, email -> email));
    Map<String, ErrorCode> failures = new LinkedHashMap<>();
    locations.notFound().forEach(id -> failures.put(id, ErrorCode.EMAIL_NOT_FOUND));

    List<String> deletableIds = new ArrayList<>();
    for (String id : requestedIds) {
      EmailMailboxResult.EmailInfo location = locationsById.get(id);
      if (location == null) {
        continue;
      }
      if (location.mailboxIds().size() == 1 && location.mailboxIds().containsKey(trashId)) {
        deletableIds.add(id);
      } else {
        failures.put(id, ErrorCode.EMAIL_NOT_EXCLUSIVELY_IN_TRASH);
      }
    }

    if (deletableIds.isEmpty()) {
      return batchResult(requestedIds, List.of(), failures);
    }

    EmailUpdateResult deleteResult = jmapClient.destroyEmails(session, deletableIds);
    deleteResult
        .failures()
        .forEach(failure -> failures.put(failure.id(), updateErrorCode(failure.type())));
    restoreRepository.deleteAll(accountId, deleteResult.updatedIds());
    return batchResult(requestedIds, deleteResult.updatedIds(), failures);
  }

  private void validateIds(List<String> ids) {
    if (ids == null
        || ids.isEmpty()
        || ids.size() > 100
        || ids.stream().anyMatch(id -> id == null || id.isBlank())
        || new HashSet<>(ids).size() != ids.size()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
  }

  private ErrorCode updateErrorCode(String type) {
    return "notFound".equals(type) ? ErrorCode.EMAIL_NOT_FOUND : ErrorCode.MAIL_SERVICE_UNAVAILABLE;
  }

  private String requireMailAccountId(JmapSession session) {
    String accountId =
        session == null || session.primaryAccounts() == null
            ? null
            : session.primaryAccounts().get("urn:ietf:params:jmap:mail");
    if (accountId == null || accountId.isBlank()) {
      throw mailServiceUnavailable();
    }
    return accountId;
  }

  private MailboxContext getMailboxContext(JmapSession session) {
    MailboxGetResult result = jmapClient.getMailboxes(session);
    if (result == null || result.list() == null) {
      throw mailServiceUnavailable();
    }

    Map<String, List<String>> idsByRole =
        result.list().stream()
            .filter(mailbox -> mailbox.role() != null && !mailbox.role().isBlank())
            .collect(
                Collectors.groupingBy(
                    MailboxGetResult.MailboxInfo::role,
                    Collectors.mapping(MailboxGetResult.MailboxInfo::id, Collectors.toList())));
    if (idsByRole.values().stream().anyMatch(roleIds -> roleIds.size() > 1)) {
      throw mailServiceUnavailable();
    }
    return new MailboxContext(
        result.list().stream().map(MailboxGetResult.MailboxInfo::id).collect(Collectors.toSet()),
        idsByRole);
  }

  private MailBatchUpdateResult batchResult(
      List<String> requestedIds, List<String> updatedIds, Map<String, ErrorCode> failures) {
    Set<String> updated = Set.copyOf(updatedIds);
    return new MailBatchUpdateResult(
        requestedIds.stream().filter(updated::contains).toList(),
        requestedIds.stream()
            .filter(failures::containsKey)
            .map(
                id -> {
                  ErrorCode errorCode = failures.get(id);
                  return new MailBatchUpdateResult.Failure(
                      id, errorCode.getCode(), errorCode.getMessage());
                })
            .toList());
  }

  private Set<String> intersection(Set<String> left, Set<String> right) {
    Set<String> result = new HashSet<>(left);
    result.retainAll(right);
    return result;
  }

  private void cleanupRestoreRecords(String accountId, Set<String> emailIds) {
    if (!emailIds.isEmpty()) {
      restoreRepository.deleteAll(accountId, new ArrayList<>(emailIds));
    }
  }

  private BusinessException mailServiceUnavailable() {
    return new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE);
  }

  private record MailboxContext(Set<String> availableIds, Map<String, List<String>> idsByRole) {

    private String requireRole(String role) {
      List<String> ids = idsByRole.get(role);
      if (ids == null || ids.size() != 1) {
        throw new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE);
      }
      return ids.getFirst();
    }
  }
}
