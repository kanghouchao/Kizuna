package com.kizuna.point.application;

import com.kizuna.point.domain.MemberLedgerEntryView;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.point.domain.PointEntryType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  /** 明細 1 行。仕訳の ID は出さない。 */
  public record LedgerEntry(
      OffsetDateTime occurredAt,
      PointEntryType entryType,
      int amount,
      LocalDate expiresOn,
      String storeName,
      String reason,
      String actorName) {}

  /** 会員 1 人の明細（全種別・全店舗）。 */
  @Transactional(readOnly = true)
  public Page<LedgerEntry> entries(long memberId, Pageable pageable) {
    return pointEntryRepository
        .findLedgerEntryViews(memberId, pageable)
        .map(MemberLedgerQueryService::toEntry);
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
