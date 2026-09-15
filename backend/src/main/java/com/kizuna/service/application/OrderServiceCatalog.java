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
public class OrderServiceCatalog {
  public enum SelectionKind {
    COURSE,
    SURCHARGE
  }

  private final ServiceItemRepository items;
  private final ServiceRevisionRepository revisions;

  @StoreScoped
  @Transactional
  public OrderServiceTerms current(String id, SelectionKind kind) {
    var item = items.findForUpdate(id).orElseThrow(() -> new NotFoundException("サービスが見つかりません"));
    if (item.isDeleted() || item.getTerms().getKind() != ServiceKind.valueOf(kind.name()))
      throw new NotFoundException("選択できるサービスが見つかりません");
    return revisions
        .findSelection(ServiceKind.valueOf(kind.name()), id, item.getRevisionNumber())
        .orElseThrow(() -> new NotFoundException("サービスの版本が見つかりません"));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public OrderServiceTerms historical(String revisionId, SelectionKind kind) {
    return revisions
        .findHistoricalSelection(ServiceKind.valueOf(kind.name()), revisionId)
        .orElseThrow(() -> new NotFoundException("サービスの歴史版本が見つかりません"));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<OrderServiceTerms> candidates(SelectionKind kind, String search, int page, int size) {
    requirePage(page, size);
    return revisions.findCurrentSelections(
        ServiceKind.valueOf(kind.name()), search == null ? "" : search, PageRequest.of(page, size));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<OrderServiceTerms> history(
      SelectionKind kind, String search, String cursor, int size) {
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
        revisions.findHistoricalSelections(
            ServiceKind.valueOf(kind.name()),
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
