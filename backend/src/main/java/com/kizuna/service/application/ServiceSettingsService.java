package com.kizuna.service.application;

import com.kizuna.service.api.dto.ServiceCreateRequest;
import com.kizuna.service.api.dto.ServiceMapper;
import com.kizuna.service.api.dto.ServiceResponse;
import com.kizuna.service.api.dto.ServiceRevisionResponse;
import com.kizuna.service.api.dto.ServiceSummary;
import com.kizuna.service.api.dto.ServiceUpdateRequest;
import com.kizuna.service.domain.ServiceItem;
import com.kizuna.service.domain.ServiceItemRepository;
import com.kizuna.service.domain.ServiceKind;
import com.kizuna.service.domain.ServiceRevision;
import com.kizuna.service.domain.ServiceRevisionRepository;
import com.kizuna.service.domain.ServiceTerms;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceSettingsService {
  private final ServiceItemRepository items;
  private final ServiceRevisionRepository revisions;
  private final ServiceMapper mapper;
  private final ActorIdentityService actors;
  private final StoreRepository stores;
  private final BusinessAudit audit;

  @Transactional(readOnly = true)
  public Page<ServiceSummary> list(
      Long storeId, ServiceKind kind, boolean deleted, int page, int size) {
    if (page < 0 || size < 1 || size > CursorPage.MAX_SIZE)
      throw new ServiceException("ページ番号と取得件数が不正です");
    Specification<ServiceItem> filter =
        (root, query, cb) ->
            cb.and(cb.equal(root.get("storeId"), storeId), cb.equal(root.get("deleted"), deleted));
    if (kind != null)
      filter = filter.and((root, query, cb) -> cb.equal(root.get("terms").get("kind"), kind));
    return items
        .findAll(filter, PageRequest.of(page, size, Sort.by(ServiceItem::getId)))
        .map(mapper::summary);
  }

  @Transactional(readOnly = true)
  public ServiceResponse get(Long storeId, String id) {
    return mapper.response(require(storeId, id));
  }

  @Transactional
  public String create(Long storeId, ServiceCreateRequest request, String actor) {
    stores.lockCastFields(storeId);
    var actorId = actors.requireUserId(actor);
    var item =
        ServiceItem.create(
            storeId,
            new ServiceTerms(
                request.kind(),
                request.name(),
                request.durationMinutes(),
                request.chargeType(),
                request.price(),
                request.remuneration()));
    items.saveAndFlush(item);
    var revision = revisions.save(ServiceRevision.record(item, null, actorId));
    audit.recordById(
        actorId,
        item.getStoreId(),
        "SERVICE_CREATED",
        "SERVICE",
        item.getId(),
        "SERVICE_REVISION",
        revision.getId(),
        Map.of(),
        auditValues(item));
    return item.getId();
  }

  @Transactional
  public ServiceResponse update(
      Long storeId, String id, ServiceUpdateRequest request, String actor) {
    stores.lockCastFields(storeId);
    var actorId = actors.requireUserId(actor);
    var item = lock(storeId, id);
    var before = item.getTerms();
    var beforeValues = auditValues(item);
    var next =
        new ServiceTerms(
            before.getKind(),
            request.name(),
            request.durationMinutes(),
            request.chargeType(),
            request.price(),
            request.remuneration());
    if (item.replace(next, request.expectedVersion())) {
      var revision = revisions.save(ServiceRevision.record(item, before, actorId));
      items.flush();
      var after = new HashMap<>(auditValues(item));
      after.put("redacted_fields_changed", changedFields(before, next));
      audit.recordById(
          actorId,
          item.getStoreId(),
          "SERVICE_UPDATED",
          "SERVICE",
          item.getId(),
          "SERVICE_REVISION",
          revision.getId(),
          beforeValues,
          after);
    }
    return mapper.response(item);
  }

  @Transactional
  public void delete(Long storeId, String id, long expectedVersion, String actor) {
    if (expectedVersion < 1) throw new ServiceException("版本は 1 以上で指定してください");
    stores.lockCastFields(storeId);
    var actorId = actors.requireUserId(actor);
    var item = lock(storeId, id);
    var before = item.getTerms();
    var beforeValues = auditValues(item);
    item.delete(expectedVersion);
    var revision = revisions.save(ServiceRevision.record(item, before, actorId));
    items.flush();
    audit.recordById(
        actorId,
        item.getStoreId(),
        "SERVICE_DELETED",
        "SERVICE",
        item.getId(),
        "SERVICE_REVISION",
        revision.getId(),
        beforeValues,
        auditValues(item));
  }

  @Transactional(readOnly = true)
  public CursorPage<ServiceRevisionResponse> history(
      Long storeId, String id, String cursor, int requestedSize) {
    require(storeId, id);
    int size = CursorPage.clampSize(requestedSize);
    var limit = Limit.of(size + 1);
    var found =
        cursor == null
            ? revisions.findByStoreIdAndServiceIdOrderByRevisionNumberDesc(storeId, id, limit)
            : revisions.findByStoreIdAndServiceIdAndRevisionNumberLessThanOrderByRevisionNumberDesc(
                storeId, id, decodeVersion(cursor), limit);
    return CursorPage.of(
            found,
            size,
            revision -> PageCursor.encodeKey(Long.toString(revision.getRevisionNumber())))
        .map(mapper::revision);
  }

  private static String changedFields(ServiceTerms before, ServiceTerms after) {
    var names = new ArrayList<String>();
    if (!Objects.equals(before.getName(), after.getName())) names.add("name");
    if (!Objects.equals(before.getDurationMinutes(), after.getDurationMinutes()))
      names.add("duration_minutes");
    if (before.getChargeType() != after.getChargeType()) names.add("charge_type");
    if (!Objects.equals(before.getPrice(), after.getPrice())) names.add("price");
    if (!Objects.equals(before.getRemuneration(), after.getRemuneration()))
      names.add("remuneration");
    return String.join(",", names);
  }

  private static Map<String, String> auditValues(ServiceItem item) {
    return Map.of(
        "exists",
        "true",
        "deleted",
        String.valueOf(item.isDeleted()),
        "version",
        String.valueOf(item.getVersion()),
        "revision_number",
        String.valueOf(item.getRevisionNumber()),
        "terms_version",
        String.valueOf(item.getTermsVersion()));
  }

  private static long decodeVersion(String cursor) {
    try {
      long version = Long.parseLong(PageCursor.decodeKey(cursor));
      if (version < 1) throw new NumberFormatException();
      return version;
    } catch (NumberFormatException exception) {
      throw new ServiceException("続きの位置（cursor）が不正です");
    }
  }

  private ServiceItem require(Long storeId, String id) {
    return items
        .findByIdAndStoreId(id, storeId)
        .orElseThrow(() -> new NotFoundException("サービスが見つかりません"));
  }

  private ServiceItem lock(Long storeId, String id) {
    return items
        .findByIdAndStoreIdForUpdate(id, storeId)
        .orElseThrow(() -> new NotFoundException("サービスが見つかりません"));
  }
}
