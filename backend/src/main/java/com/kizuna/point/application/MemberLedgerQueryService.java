package com.kizuna.point.application;

import com.kizuna.point.domain.MemberLedgerEntryView;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.point.domain.PointEntryType;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 職員の会員コンソール向けの明細の読み口。台帳は店舗で分割しない（ADR 0006）ので、会員 ID の一致だけが境界で、発生店舗は表示属性として添える。
 *
 * <p>本人向け（{@link MemberPointService}）と別に置くのは、出す項目が違うため — 理由と実行者は運用側の照会にだけ要る。書き込みの口は持たない。
 */
@Service
@RequiredArgsConstructor
public class MemberLedgerQueryService {

  private final PointEntryRepository pointEntryRepository;

  /** 明細 1 行。仕訳の ID は出さない — 続きの位置はカーソルが運ぶ。 */
  public record LedgerEntry(
      OffsetDateTime occurredAt,
      PointEntryType entryType,
      int amount,
      LocalDate expiresOn,
      String storeName,
      String reason,
      String actorName) {}

  /** 会員 1 人の明細（全種別・全店舗）を新しい順に。続きはカーソルで指す（本人向けと同じ理由）。 */
  @Transactional(readOnly = true)
  public CursorPage<LedgerEntry> entries(long memberId, String cursor, int requestedSize) {
    int size = CursorPage.clampSize(requestedSize);
    Limit limit = Limit.of(size + 1);
    List<MemberLedgerEntryView> fetched =
        cursor == null
            ? pointEntryRepository.findLedgerEntryViews(memberId, limit)
            : fetchAfter(memberId, PageCursor.decode(cursor), limit);
    return CursorPage.of(fetched, size, MemberLedgerQueryService::cursorOf)
        .map(MemberLedgerQueryService::toEntry);
  }

  private List<MemberLedgerEntryView> fetchAfter(Long memberId, PageCursor cursor, Limit limit) {
    return pointEntryRepository.findLedgerEntryViewsAfter(
        memberId, cursor.timestampKey(), cursor.longId(), limit);
  }

  private static String cursorOf(MemberLedgerEntryView view) {
    return new PageCursor(view.getCreatedAt().toString(), String.valueOf(view.getId())).encode();
  }

  private static LedgerEntry toEntry(MemberLedgerEntryView view) {
    return new LedgerEntry(
        view.getCreatedAt(),
        view.getEntryType(),
        view.getAmount(),
        view.getExpiresOn(),
        view.getStoreName(),
        view.getReason(),
        view.getActorName());
  }
}
