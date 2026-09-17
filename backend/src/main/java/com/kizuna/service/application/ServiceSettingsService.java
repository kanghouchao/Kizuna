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
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.ActorIdentityService;
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
  private final StoreContext context;

  @Transactional(readOnly = true)
  @StoreScoped
  public Page<ServiceSummary> list(ServiceKind kind, boolean deleted, int page, int size) {
    if (page < 0 || size < 1 || size > CursorPage.MAX_SIZE)
      throw new ServiceException("ページ番号と取得件数が不正です");
    Specification<ServiceItem> filter = (root, query, cb) -> cb.equal(root.get("deleted"), deleted);
    if (kind != null)
      filter = filter.and((root, query, cb) -> cb.equal(root.get("terms").get("kind"), kind));
    return items
        .findAll(filter, PageRequest.of(page, size, Sort.by(ServiceItem::getId)))
        .map(mapper::summary);
  }

  @Transactional(readOnly = true)
  @StoreScoped
  public ServiceResponse get(String id) {
    return mapper.response(require(id));
  }

  @Transactional
  @StoreScoped
  public String create(ServiceCreateRequest request, String actor) {
    stores.lockCastFields(context.getStoreId());
    var actorId = actors.requireUserId(actor);
    var item =
        ServiceItem.create(
            new ServiceTerms(
                request.kind(),
                request.name(),
                request.durationMinutes(),
                request.chargeType(),
                request.price(),
                request.remuneration()));
    items.saveAndFlush(item);
    revisions.save(ServiceRevision.record(item, null, actorId));
    return item.getId();
  }

  @Transactional
  @StoreScoped
  public ServiceResponse update(String id, ServiceUpdateRequest request, String actor) {
    stores.lockCastFields(context.getStoreId());
    var actorId = actors.requireUserId(actor);
    var item = lock(id);
    var before = item.getTerms();
    var next =
        new ServiceTerms(
            before.getKind(),
            request.name(),
            request.durationMinutes(),
            request.chargeType(),
            request.price(),
            request.remuneration());
    if (item.replace(next, request.expectedVersion())) {
      revisions.save(ServiceRevision.record(item, before, actorId));
      items.flush();
    }
    return mapper.response(item);
  }

  @Transactional
  @StoreScoped
  public void delete(String id, long expectedVersion, String actor) {
    if (expectedVersion < 1) throw new ServiceException("版本は 1 以上で指定してください");
    stores.lockCastFields(context.getStoreId());
    var actorId = actors.requireUserId(actor);
    var item = lock(id);
    var before = item.getTerms();
    item.delete(expectedVersion);
    revisions.save(ServiceRevision.record(item, before, actorId));
  }

  @Transactional(readOnly = true)
  @StoreScoped
  public CursorPage<ServiceRevisionResponse> history(String id, String cursor, int requestedSize) {
    require(id);
    int size = CursorPage.clampSize(requestedSize);
    var limit = Limit.of(size + 1);
    var found =
        cursor == null
            ? revisions.findByServiceIdOrderByRevisionNumberDesc(id, limit)
            : revisions.findByServiceIdAndRevisionNumberLessThanOrderByRevisionNumberDesc(
                id, decodeVersion(cursor), limit);
    return CursorPage.of(
            found,
            size,
            revision -> PageCursor.encodeKey(Long.toString(revision.getRevisionNumber())))
        .map(mapper::revision);
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

  private ServiceItem require(String id) {
    return items.findById(id).orElseThrow(() -> new NotFoundException("サービスが見つかりません"));
  }

  private ServiceItem lock(String id) {
    return items.findForUpdate(id).orElseThrow(() -> new NotFoundException("サービスが見つかりません"));
  }
}
