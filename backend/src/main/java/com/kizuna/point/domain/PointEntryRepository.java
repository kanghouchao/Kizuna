package com.kizuna.point.domain;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointEntryRepository extends JpaRepository<PointEntry, Long> {

  // ロットの述語は「加算」ではなく「加算かつ新しいロットになる」。利用取消は正だが元のロットへ量を返す
  // だけで自身はロットにならず、ロットとして数えると返した分が残高に二重に現れる。エンティティ名は HQL の
  // 予約語衝突を避けるため FQCN で参照する。
  String LOT_WHERE =
      " where e.memberId = :memberId and e.amount > 0"
          + " and e.entryType <> com.kizuna.point.domain.PointEntryType.USE_CANCEL";

  /** 会員の加算ロット（残高照会用、ロックなし）。 */
  @Query("select e from com.kizuna.point.domain.PointEntry e" + LOT_WHERE)
  List<PointEntry> findCredits(@Param("memberId") Long memberId);

  /**
   * 会員の加算ロットを悲観排他ロック（SELECT ... FOR UPDATE）付きで取得する。
   *
   * <p>「残高は 0 未満にならない」は行を跨いだ合計の不変量で、DB 制約では守れない。同一会員の消費を直列化することで、 並行する 2
   * つの消費が同じ残りを二重に引き当てるのを防ぐ。読むだけの経路は {@link #findCredits} を使う。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from com.kizuna.point.domain.PointEntry e" + LOT_WHERE)
  List<PointEntry> findCreditsForUpdate(@Param("memberId") Long memberId);

  /** 受注を根拠とする加算ロット。受注からその付与を辿って取り消す経路の入口。 */
  @Query(
      "select e from com.kizuna.point.domain.PointEntry e where e.orderId = :orderId"
          + " and e.amount > 0")
  List<PointEntry> findCreditsByOrderId(@Param("orderId") String orderId);

  /**
   * 会員 1 人の、受注ごとの付与合計。加算で受注 ID を持つ仕訳だけを足す — 付与の経路（完了時・事後申領）を種別で区別せず、{@link #findCreditsByOrderId}
   * と同じ読み方で拾う。取消は元の付与を打ち消す減算として別の行に積まれ、この合計には現れない。
   *
   * <p>会員 ID の一致を条件に載せるのは、1
   * 件の受注に別々の会員の付与が並びうるため。誤帰属を無効化しても付与行は台帳に残る（訂正は別段の手動調整）ので、その受注が正しい本人へ申領されると 受注 ID
   * だけでは前の会員の付与まで足してしまう。
   *
   * <p>合計は long で返す。1 件の仕訳は int でも、1 受注に複数の付与が積まれた合計は int を超えうる。
   */
  @Query(
      "select e.orderId as orderId, sum(e.amount) as total"
          + " from com.kizuna.point.domain.PointEntry e"
          + " where e.memberId = :memberId and e.orderId in :orderIds and e.amount > 0"
          + " group by e.orderId")
  List<OrderGrantTotalView> sumGrantsByOrderIds(
      @Param("memberId") Long memberId, @Param("orderIds") Collection<String> orderIds);

  /** 受注会計で積まれた利用。巻き戻しが逆転する対象で、加算側とは別に引く。 */
  @Query(
      "select e from com.kizuna.point.domain.PointEntry e where e.orderId = :orderId"
          + " and e.entryType = com.kizuna.point.domain.PointEntryType.USE")
  List<PointEntry> findUsesByOrderId(@Param("orderId") String orderId);

  /** 与えた利用のうち、既に逆転済みのものの ID。逆転は 1 件につき高々 1 回なので有無だけで足りる。 */
  @Query(
      "select e.originalEntryId from com.kizuna.point.domain.PointEntry e"
          + " where e.entryType = com.kizuna.point.domain.PointEntryType.USE_CANCEL"
          + " and e.originalEntryId in :useIds")
  List<Long> findReversedUseIds(@Param("useIds") Collection<Long> useIds);

  /**
   * 会員の受注付与の累計純額。会員ランクの昇格指標で、取消によって減りうる。
   *
   * <p>控除するのは付与を打ち消す取消（CANCEL）だけを {@code originalEntryId} で辿って数える — 種別だけで数えると手動調整の取消まで引いてしまう。 合計が
   * long なのは残高と同じ理由。
   */
  @Query(
      "select coalesce(sum(e.amount), 0) from com.kizuna.point.domain.PointEntry e"
          + " where e.memberId = :memberId"
          + " and (e.entryType = com.kizuna.point.domain.PointEntryType.ORDER_GRANT"
          + " or (e.entryType = com.kizuna.point.domain.PointEntryType.CANCEL"
          + " and e.originalEntryId in ("
          + " select g.id from com.kizuna.point.domain.PointEntry g"
          + " where g.memberId = :memberId"
          + " and g.entryType = com.kizuna.point.domain.PointEntryType.ORDER_GRANT)))")
  long sumNetOrderGrants(@Param("memberId") Long memberId);

  /**
   * 与えた加算ロットのうち、その根拠の受注が既に巻き戻されているもの。
   *
   * <p>利用の逆転は消費量を元のロットへ返すため、返した先が巻き戻し済みの受注の付与だと、無効にしたはずの量が
   * 使える残高として復活する。相手の受注は二度目の巻き戻しを受け付けないので、返した側が同じ取引で打ち消す。
   */
  @Query(
      "select e from com.kizuna.point.domain.PointEntry e"
          + " where e.id in :creditIds and e.orderId is not null"
          + " and exists (select 1 from com.kizuna.point.domain.PointRollback r"
          + " where r.orderId = e.orderId)")
  List<PointEntry> findRolledBackCreditsAmong(@Param("creditIds") Collection<Long> creditIds);

  /** 冪等キーで初回の手動調整を引く。再送の判定入口（ADR 0007）。 */
  Optional<PointEntry> findByIdempotencyKey(String idempotencyKey);

  /**
   * 指定した帰属記録群に対して既に積まれた訂正の合計。訂正は減算なので負で返る。
   *
   * <p>記録 1 件ではなく<b>群</b>で受けるのは、この合計が {@link #sumGrantsByOrderIds} の付与合計と引き比べられるため。
   * 付与は受注と会員で数えるので、訂正も同じ受注・同じ会員に属する記録すべてを数えないと、両辺の作用域がずれる。 ずれると、同じ会員が同じ受注で二度帰属した場合（無効化 → 再発行 →
   * 本人が申領し直す）に、片方の帰属の訂正枠が もう片方の付与まで飲み込む。
   *
   * <p>同じ冪等キーの行を除いて数えるのは、応答を取り逃した正当な再送が累計上限の超過で撥ねられるのを防ぐため。 初回が記帳済みでも、再送が自分自身を数えなければ初回と同じ判定に落ちる（ADR
   * 0007 が「再送の判定を入力検証より 先に置く」ことで一度回避したのと同型の罠）。訂正の仕訳は手動調整であり冪等キーを必ず持つので、キーが null の行を場合分けする必要は無い。
   *
   * <p>合計を long で返すのは残高と同じ理由 — 1 件の仕訳は int でも積み上げた合計は int を超えうる。
   */
  @Query(
      "select coalesce(sum(e.amount), 0) from com.kizuna.point.domain.PointEntry e"
          + " where e.correctedAttributionId in :attributionIds"
          + " and e.idempotencyKey <> :excludeIdempotencyKey")
  long sumCorrectionsExcludingKey(
      @Param("attributionIds") Collection<Long> attributionIds,
      @Param("excludeIdempotencyKey") String excludeIdempotencyKey);

  /** 指定した帰属記録群に対して積まれた訂正の合計。訂正は減算なので負で返る。進み具合の表示に使う。 */
  @Query(
      "select coalesce(sum(e.amount), 0) from com.kizuna.point.domain.PointEntry e"
          + " where e.correctedAttributionId in :attributionIds")
  long sumCorrections(@Param("attributionIds") Collection<Long> attributionIds);

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
