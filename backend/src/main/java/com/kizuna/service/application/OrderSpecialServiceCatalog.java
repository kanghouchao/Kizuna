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
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderSpecialServiceCatalog {
  private final ServiceItemRepository items;
  private final ServiceRevisionRepository revisions;
  private final ServiceConsentRepository consents;
  private final ServiceConsentEventRepository events;
  private final CastEnrollmentRepository enrollments;

  @StoreScoped
  @Transactional(readOnly = true)
  public SpecialServiceTerms current(String enrollment, String serviceId) {
    requireEnrollment(enrollment);
    var item =
        items
            .findById(serviceId)
            .filter(i -> !i.isDeleted())
            .orElseThrow(() -> new NotFoundException("特殊サービスが見つかりません"));
    var consent =
        consents
            .findByEnrollmentIdAndServiceId(enrollment, serviceId)
            .filter(c -> c.status(item.getTermsVersion()) == ConsentStatus.ACCEPTED)
            .orElseThrow(() -> new ServiceException("担当在籍が現在の条件を受諾していません"));
    var revision =
        revisions
            .findByServiceIdAndRevisionNumber(serviceId, item.getRevisionNumber())
            .orElseThrow(() -> new NotFoundException("特殊サービスの版本が見つかりません"));
    return terms(
        revisions
            .findHistoricalSpecial(revision.getId())
            .orElseThrow(() -> new NotFoundException("特殊サービスが見つかりません")),
        consent);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<SpecialServiceTerms> candidates(
      String enrollment, String search, int page, int size) {
    requirePage(page, size);
    requireEnrollment(enrollment);
    return revisions
        .findAcceptedSpecials(enrollment, search == null ? "" : search, PageRequest.of(page, size))
        .map(
            r ->
                terms(
                    r,
                    consents
                        .findByEnrollmentIdAndServiceId(enrollment, r.getServiceId())
                        .orElseThrow()));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public SpecialServiceTerms historical(String id) {
    return terms(
        revisions
            .findHistoricalSpecial(id)
            .orElseThrow(() -> new NotFoundException("特殊サービスの歴史版本が見つかりません")),
        null);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<SpecialServiceTerms> history(String search, String cursor, int size) {
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
            search == null ? "" : search,
            at,
            key == null ? "" : key.id(),
            PageRequest.of(0, size + 1));
    return CursorPage.of(
            rows, size, r -> new PageCursor(r.getOccurredAt().toString(), r.getId()).encode())
        .map(r -> terms(r, null));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public String status(String enrollment, String serviceId) {
    var item = items.findById(serviceId).orElseThrow(() -> new NotFoundException("特殊サービスが見つかりません"));
    return consents
        .findByEnrollmentIdAndServiceId(enrollment, serviceId)
        .map(c -> c.status(item.getTermsVersion()).name())
        .orElse("NOT_ACCEPTED");
  }

  private void requireEnrollment(String id) {
    if (id == null) throw new ServiceException("特殊サービスの担当を選択してください");
    var enrollment =
        enrollments.findById(id).orElseThrow(() -> new NotFoundException("在籍が見つかりません"));
    if (enrollment.getStatus() != CastEnrollmentStatus.ENROLLED)
      throw new ServiceException("担当在籍が提供できる状態ではありません");
  }

  private SpecialServiceTerms terms(ServiceRevision r, ServiceConsent consent) {
    var t = r.getAfterTerms();
    var event =
        consent == null
            ? null
            : events
                .findByConsentIdAndRevisionNumber(consent.getId(), consent.getRevisionNumber())
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
        items.findById(r.getServiceId()).orElseThrow().isDeleted());
  }

  private void requirePage(int page, int size) {
    if (page < 0 || size < 1 || size > Math.min(100, CursorPage.MAX_SIZE))
      throw new ServiceException("ページの範囲が正しくありません");
  }
}
