package com.kizuna.service.application;

import com.kizuna.service.domain.ServiceItemRepository;
import com.kizuna.service.domain.ServiceKind;
import com.kizuna.service.domain.ServiceRevisionRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

  @Transactional
  public OrderServiceTerms current(Long storeId, String id, SelectionKind kind) {
    var item =
        items
            .findByIdAndStoreIdForUpdate(id, storeId)
            .orElseThrow(() -> new NotFoundException("サービスが見つかりません"));
    if (item.isDeleted() || item.getTerms().getKind() != ServiceKind.valueOf(kind.name()))
      throw new NotFoundException("選択できるサービスが見つかりません");
    return revisions
        .findSelection(storeId, ServiceKind.valueOf(kind.name()), id, item.getRevisionNumber())
        .orElseThrow(() -> new NotFoundException("サービスの版本が見つかりません"));
  }

  @Transactional(readOnly = true)
  public OrderServiceTerms historical(Long storeId, String revisionId, SelectionKind kind) {
    return revisions
        .findHistoricalSelection(storeId, ServiceKind.valueOf(kind.name()), revisionId)
        .orElseThrow(() -> new NotFoundException("サービスの歴史版本が見つかりません"));
  }

  @Transactional(readOnly = true)
  public Page<OrderServiceTerms> candidates(
      Long storeId, SelectionKind kind, String search, int page, int size) {
    requirePage(page, size);
    return revisions.findCurrentSelections(
        storeId,
        ServiceKind.valueOf(kind.name()),
        search == null ? "" : search,
        PageRequest.of(page, size));
  }

  @Transactional(readOnly = true)
  public Page<OrderServiceTerms> history(
      Long storeId, SelectionKind kind, String search, Pageable pageable) {
    return revisions.findHistoricalSelections(
        storeId, ServiceKind.valueOf(kind.name()), search == null ? "" : search, pageable);
  }

  private void requirePage(int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw new ServiceException("ページの範囲が正しくありません");
  }
}
