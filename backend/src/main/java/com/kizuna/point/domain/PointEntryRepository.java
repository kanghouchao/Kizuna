package com.kizuna.point.domain;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointEntryRepository extends JpaRepository<PointEntry, Long> {

  /** 会員の加算ロット（残高照会用、ロックなし）。エンティティ名は HQL の予約語衝突を避けるため FQCN で参照する。 */
  @Query(
      "select e from com.kizuna.point.domain.PointEntry e where e.memberId = :memberId"
          + " and e.amount > 0")
  List<PointEntry> findCredits(@Param("memberId") Long memberId);

  /**
   * 会員の加算ロットを悲観排他ロック（SELECT ... FOR UPDATE）付きで取得する。
   *
   * <p>「残高は 0 未満にならない」は行を跨いだ合計の不変量で、DB 制約では守れない。同一会員の消費を直列化することで、 並行する 2
   * つの消費が同じ残りを二重に引き当てるのを防ぐ。読むだけの経路は {@link #findCredits} を使う。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select e from com.kizuna.point.domain.PointEntry e where e.memberId = :memberId"
          + " and e.amount > 0")
  List<PointEntry> findCreditsForUpdate(@Param("memberId") Long memberId);

  /** 受注を根拠とする加算ロット。受注からその付与を辿って取り消す経路の入口。 */
  @Query(
      "select e from com.kizuna.point.domain.PointEntry e where e.orderId = :orderId"
          + " and e.amount > 0")
  List<PointEntry> findCreditsByOrderId(@Param("orderId") String orderId);

  /** 冪等キーで初回の手動調整を引く。再送の判定入口（ADR 0007）。 */
  Optional<PointEntry> findByIdempotencyKey(String idempotencyKey);

  /** 指定店舗に帰属する仕訳が 1 件でも存在するか。符号は問わない（取消も含めて記録の存在そのものを見る）。 */
  @Query(
      "select count(e) > 0 from com.kizuna.point.domain.PointEntry e"
          + " where e.originatingStoreId = :storeId")
  boolean existsByOriginatingStoreId(@Param("storeId") Long storeId);

  // 会員本人の明細一覧。発生店舗名は列に持たず、表示が要るときだけ store から導出する（ADR 0003 と同じ
  // 扱い。台帳は platform 帰属なので同 ADR が名指す店舗作用域エンティティそのものではない）。left join
  // なのは、失効のように発生店舗を持たない仕訳と、店舗が消えた後の仕訳（FK は SET NULL）でも行そのものを
  // 落とさないため。
  String MEMBER_ENTRY_SELECT =
      """
      select e.id as id, e.createdAt as createdAt, e.entryType as entryType,
             e.amount as amount, e.expiresOn as expiresOn, st.name as storeName
      from com.kizuna.point.domain.PointEntry e
        left join com.kizuna.store.domain.Store st on st.id = e.originatingStoreId
      """;

  // 台帳は店舗で分割しない（ADR 0006）ため storeFilter は働かない。本人の会員 ID の一致が唯一の隔離境界で、
  // 先頭と続きの問い合わせが同じ条件を共有する。
  String MEMBER_ENTRY_WHERE = " where e.memberId = :memberId ";

  // 新しい仕訳から。カーソルの比較（下記 AFTER 条件）はこの並びと同じ列の組で行う。
  String MEMBER_ENTRY_ORDER = " order by e.createdAt desc, e.id desc";

  /**
   * 会員本人のポイント明細（全種別・跨店集約）の先頭。
   *
   * <p>並びは記帳時刻の降順に一意な副キー id を重ねて全順序にする（カーソルが 1 行を一意に指せるため）。
   */
  @Query(MEMBER_ENTRY_SELECT + MEMBER_ENTRY_WHERE + MEMBER_ENTRY_ORDER)
  List<MemberPointEntryView> findMemberEntryViews(@Param("memberId") Long memberId, Limit limit);

  /** 会員本人のポイント明細の続き。渡された位置より後ろ（＝より古い側）だけを返す。 */
  @Query(
      MEMBER_ENTRY_SELECT
          + MEMBER_ENTRY_WHERE
          + """
            and (e.createdAt < :cursorCreatedAt
                 or (e.createdAt = :cursorCreatedAt and e.id < :cursorId))
            """
          + MEMBER_ENTRY_ORDER)
  List<MemberPointEntryView> findMemberEntryViewsAfter(
      @Param("memberId") Long memberId,
      @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      Limit limit);
}
