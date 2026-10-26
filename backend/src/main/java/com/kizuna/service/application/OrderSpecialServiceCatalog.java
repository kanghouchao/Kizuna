package com.kizuna.service.application;

import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.service.domain.ConsentStatus;
import com.kizuna.service.domain.ServiceConsent;
import com.kizuna.service.domain.ServiceConsentEventRepository;
import com.kizuna.service.domain.ServiceConsentRepository;
import com.kizuna.service.domain.ServiceItemRepository;
import com.kizuna.service.domain.ServiceRevision;
import com.kizuna.service.domain.ServiceRevisionRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderSpecialServiceCatalog {
  private final ServiceItemRepository items;
  private final ServiceRevisionRepository revisions;
  private final ServiceConsentRepository consents;
  private final ServiceConsentEventRepository events;
  private final CastEnrollmentRepository enrollments;

  @Transactional(readOnly = true)
  public SpecialServiceTerms current(Long storeId, String enrollment, String serviceId) {
    requireEnrollment(storeId, enrollment);
    var item =
        items
            .findByIdAndStoreId(serviceId, storeId)
            .filter(i -> !i.isDeleted())
            .orElseThrow(() -> new NotFoundException("特殊サービスが見つかりません"));
    var consent =
        consents
            .findByStoreIdAndEnrollmentIdAndServiceId(storeId, enrollment, serviceId)
            .filter(c -> c.status(item.getTermsVersion()) == ConsentStatus.ACCEPTED)
            .orElseThrow(() -> new ServiceException("担当在籍が現在の条件を受諾していません"));
    var revision =
        revisions
            .findByStoreIdAndServiceIdAndRevisionNumber(
                storeId, serviceId, item.getRevisionNumber())
            .orElseThrow(() -> new NotFoundException("特殊サービスの版本が見つかりません"));
    return terms(
        storeId,
        revisions
            .findHistoricalSpecial(storeId, revision.getId())
            .orElseThrow(() -> new NotFoundException("特殊サービスが見つかりません")),
        consent);
  }

  /** 在籍資格・候補・総数を同じ断面で判定する。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<SpecialServiceTerms> candidates(
      Long storeId, String enrollment, String search, int page, int size) {
    requirePage(page, size);
    requireEnrollment(storeId, enrollment);
    return revisions.findAcceptedSpecials(
        storeId, enrollment, search == null ? "" : search, PageRequest.of(page, size));
  }

  @Transactional(readOnly = true)
  public SpecialServiceTerms historical(Long storeId, String id) {
    return terms(
        storeId,
        revisions
            .findHistoricalSpecial(storeId, id)
            .orElseThrow(() -> new NotFoundException("特殊サービスの歴史版本が見つかりません")),
        null);
  }

  /** 歴史版本と各項目の現在の削除状態を同じ断面から返す。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CursorPage<SpecialServiceTerms> history(
      Long storeId, String search, String cursor, int size) {
    requirePage(0, size);
    var key = cursor == null ? null : PageCursor.decode(cursor);
    OffsetDateTime at;
    try {
      at =
          key == null
              ? OffsetDateTime.parse("9999-12-31T23:59:59Z")
              : OffsetDateTime.parse(key.key());
    } catch (RuntimeException ex) {
      throw new ServiceException("カーソルが正しくありません");
    }
    var rows =
        revisions.findHistoricalSpecials(
            storeId,
            search == null ? "" : search,
            at,
            key == null ? "" : key.id(),
            PageRequest.of(0, size + 1));
    return CursorPage.of(
            rows, size, r -> new PageCursor(r.getOccurredAt().toString(), r.getId()).encode())
        .map(r -> terms(storeId, r, null));
  }

  @Transactional(readOnly = true)
  public String status(Long storeId, String enrollment, String serviceId) {
    if (enrollment == null
        || enrollments
            .findByIdAndStoreId(enrollment, storeId)
            .filter(e -> e.getStatus() == CastEnrollmentStatus.ENROLLED)
            .isEmpty()) return ConsentStatus.NOT_ACCEPTED.name();
    var item =
        items
            .findByIdAndStoreId(serviceId, storeId)
            .orElseThrow(() -> new NotFoundException("特殊サービスが見つかりません"));
    return consents
        .findByStoreIdAndEnrollmentIdAndServiceId(storeId, enrollment, serviceId)
        .map(c -> c.status(item.getTermsVersion()).name())
        .orElse("NOT_ACCEPTED");
  }

  private void requireEnrollment(Long storeId, String id) {
    if (id == null) throw new ServiceException("特殊サービスの担当を選択してください");
    var enrollment =
        enrollments
            .findByIdAndStoreId(id, storeId)
            .orElseThrow(() -> new NotFoundException("在籍が見つかりません"));
    if (enrollment.getStatus() != CastEnrollmentStatus.ENROLLED)
      throw new ServiceException("担当在籍が提供できる状態ではありません");
  }

  private SpecialServiceTerms terms(Long storeId, ServiceRevision r, ServiceConsent consent) {
    var t = r.getAfterTerms();
    var event =
        consent == null
            ? null
            : events
                .findByStoreIdAndConsentIdAndRevisionNumber(
                    storeId, consent.getId(), consent.getRevisionNumber())
                .orElseThrow();
    return new SpecialServiceTerms(
        r.getServiceId(),
        r.getId(),
        r.getRevisionNumber(),
        r.getTermsVersion(),
        t.getName(),
        t.getChargeType().name(),
        t.getPrice(),
        t.getRemuneration(),
        event == null ? null : event.getId(),
        consent == null ? null : consent.getRevisionNumber(),
        r.getOccurredAt(),
        items.findByIdAndStoreId(r.getServiceId(), storeId).orElseThrow().isDeleted());
  }

  private void requirePage(int page, int size) {
    if (page < 0 || size < 1 || size > Math.min(100, CursorPage.MAX_SIZE))
      throw new ServiceException("ページの範囲が正しくありません");
  }
}
