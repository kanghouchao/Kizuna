package com.kizuna.service.application;

import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.service.api.dto.OwnConsentRequest;
import com.kizuna.service.api.dto.OwnServiceConditionSummary;
import com.kizuna.service.domain.ConsentDecision;
import com.kizuna.service.domain.ConsentStatus;
import com.kizuna.service.domain.ServiceConsent;
import com.kizuna.service.domain.ServiceConsentEvent;
import com.kizuna.service.domain.ServiceConsentEventRepository;
import com.kizuna.service.domain.ServiceConsentRepository;
import com.kizuna.service.domain.ServiceItem;
import com.kizuna.service.domain.ServiceItemRepository;
import com.kizuna.service.domain.ServiceKind;
import com.kizuna.service.domain.ServiceRevisionRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.ActorIdentityService;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnServiceConditionService {
  private final ServiceItemRepository items;
  private final ServiceRevisionRepository revisions;
  private final ServiceConsentRepository consents;
  private final ServiceConsentEventRepository events;
  private final CastEnrollmentRepository enrollments;
  private final ActorIdentityService actors;
  private final StoreRepository stores;
  private final StoreContext context;
  private final SpecialServiceRejectionHandler rejectionHandler;

  @Transactional(readOnly = true)
  @StoreScoped
  public Page<OwnServiceConditionSummary> list(String actor, ServiceKind kind, int page, int size) {
    String enrollment = requireEnrollment(actors.requireUserId(actor));
    if (page < 0 || size < 1 || size > CursorPage.MAX_SIZE)
      throw new ServiceException("ページ番号と取得件数が不正です");
    Specification<ServiceItem> filter = (root, query, cb) -> cb.isFalse(root.get("deleted"));
    if (kind != null)
      filter = filter.and((root, query, cb) -> cb.equal(root.get("terms").get("kind"), kind));
    var found = items.findAll(filter, PageRequest.of(page, size, Sort.by("id")));
    var decisions =
        consents
            .findByEnrollmentIdAndServiceIdIn(
                enrollment, found.stream().map(ServiceItem::getId).toList())
            .stream()
            .collect(Collectors.toMap(ServiceConsent::getServiceId, Function.identity()));
    return found.map(item -> summary(item, decisions.get(item.getId())));
  }

  @Transactional
  @StoreScoped
  public OwnServiceConditionSummary decide(String actor, String id, OwnConsentRequest request) {
    stores.lockCastFields(context.getStoreId());
    Long actorId = actors.requireUserId(actor);
    String enrollment = requireEnrollment(actorId);
    var item =
        items
            .findForUpdate(id)
            .filter(value -> !value.isDeleted())
            .orElseThrow(() -> new NotFoundException("サービスが見つかりません"));
    if (item.getTerms().getKind() != ServiceKind.SPECIAL_SERVICE)
      throw new ServiceException("特殊サービスのみ受諾・拒否できます");
    if (request.termsVersion() != item.getTermsVersion())
      throw new ConflictException("条件が変更されています。最新の内容を再取得して確認してください");
    var consent =
        consents
            .findByEnrollmentIdAndServiceId(enrollment, id)
            .orElseGet(() -> ServiceConsent.create(enrollment, id));
    var revision =
        revisions.findByServiceIdAndRevisionNumber(id, item.getRevisionNumber()).orElseThrow();
    if (consent.decide(
        request.decision(), item.getTermsVersion(), revision.getId(), request.consentVersion())) {
      consents.saveAndFlush(consent);
      var event = events.saveAndFlush(ServiceConsentEvent.record(consent, actorId));
      if (request.decision() == ConsentDecision.REJECTED)
        rejectionHandler.applyRejection(
            new SpecialServiceRejection(
                enrollment, id, event.getId(), actorId, event.getOccurredAt()));
    }
    return summary(item, consent);
  }

  private String requireEnrollment(Long actorId) {
    return enrollments.findIdsByPlatformUserIdAndStoreId(actorId, context.getStoreId()).stream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("本人の有効な在籍が見つかりません"));
  }

  private OwnServiceConditionSummary summary(ServiceItem item, ServiceConsent consent) {
    var terms = item.getTerms();
    boolean special = terms.getKind() == ServiceKind.SPECIAL_SERVICE;
    return new OwnServiceConditionSummary(
        item.getId(),
        item.getStoreId().toString(),
        terms.getKind(),
        terms.getName(),
        terms.getDurationMinutes(),
        terms.getChargeType(),
        terms.getPrice(),
        terms.getRemuneration(),
        item.getTermsVersion(),
        !special
            ? null
            : consent == null ? ConsentStatus.NOT_ACCEPTED : consent.status(item.getTermsVersion()),
        !special ? null : consent == null ? 0L : consent.getRevisionNumber());
  }
}
