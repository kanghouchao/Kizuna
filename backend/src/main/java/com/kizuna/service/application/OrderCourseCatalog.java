package com.kizuna.service.application;

import com.kizuna.service.domain.ServiceItemRepository;
import com.kizuna.service.domain.ServiceKind;
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
public class OrderCourseCatalog {
  private final ServiceItemRepository items;
  private final ServiceRevisionRepository revisions;

  @StoreScoped
  @Transactional
  public CourseTerms current(String id) {
    var item = items.findForUpdate(id).orElseThrow(() -> new NotFoundException("コースが見つかりません"));
    if (item.isDeleted() || item.getTerms().getKind() != ServiceKind.COURSE)
      throw new NotFoundException("選択できるコースが見つかりません");
    return revisions
        .findCourse(id, item.getRevisionNumber())
        .orElseThrow(() -> new NotFoundException("コースの版本が見つかりません"));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CourseTerms historical(String revisionId) {
    return revisions
        .findHistoricalCourse(revisionId)
        .orElseThrow(() -> new NotFoundException("コースの歴史版本が見つかりません"));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<CourseTerms> candidates(String search, int page, int size) {
    requirePage(page, size);
    return revisions.findCurrentCourses(search == null ? "" : search, PageRequest.of(page, size));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<CourseTerms> history(String search, String cursor, int size) {
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
        revisions.findHistoricalCourses(
            search == null ? "" : search,
            at,
            key == null ? "" : key.id(),
            PageRequest.of(0, size + 1));
    return CursorPage.of(
        rows, size, row -> new PageCursor(row.occurredAt().toString(), row.revisionId()).encode());
  }

  private void requirePage(int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw new ServiceException("ページの範囲が正しくありません");
  }
}
