package com.kizuna.customer.domain;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerMemberLinkRepository extends JpaRepository<CustomerMemberLink, String> {

  /**
   * 顧客に紐づく会員（ロックなし）。書き換える経路も同じ問い合わせを使い、直列化は先に取る顧客行のロックが担う（{@code
   * CustomerRepository#findByIdForUpdate}）。
   */
  Optional<CustomerMemberLink> findByCustomerIdAndStatus(String customerId, LinkStatus status);

  List<CustomerMemberLink> findByCustomerIdInAndStatus(
      Collection<String> customerIds, LinkStatus status);

  boolean existsByMemberIdAndStatus(Long memberId, LinkStatus status);

  boolean existsByCustomerIdAndStatus(String customerId, LinkStatus status);

  /**
   * 統合の付替えで、被統合行の関連を存続行へ移す。状態では絞らない — 解除済み（RELEASED）の区間も移さなければ「過去に誰と紐づいていたか」の 履歴が統合で切れる。
   *
   * <p>顧客参照は行ごと不変の写像（{@code updatable = false}）なので、実体の setter を開かずにここで書く。通常の経路から書き換えられる形にすると、
   * 区間史の顧客が統合以外の理由で動きうることになる。
   *
   * <p>WHERE 句に店舗を明示するのは、Hibernate の {@code @Filter} が HQL の一括 UPDATE には掛からないため。
   *
   * @return 移した関連の件数（統合履歴にそのまま残す）
   */
  @Modifying
  @Query(
      """
      update com.kizuna.customer.domain.CustomerMemberLink l set l.customerId = :survivingId
      where l.customerId = :mergedId and l.storeId = :storeId
      """)
  int repointCustomer(
      @Param("survivingId") String survivingId,
      @Param("mergedId") String mergedId,
      @Param("storeId") Long storeId);

  // 会員起点の照会。店舗文脈を持たない経路（会員本人）から呼ばれるため storeFilter は働かず、
  // 引数の storeId が唯一の店舗境界になる。ACTIVE 行は部分一意索引により店舗ごとに 1 件以下。
  Optional<CustomerMemberLink> findByStoreIdAndMemberIdAndStatus(
      Long storeId, Long memberId, LinkStatus status);

  // 実行者の表示名は ID 参照のため JPQL join で取得する。PlatformUser は FQCN で参照する
  // （HQL の予約語衝突を避ける既存規約）。実行者が削除されると linked_by / released_by は
  // NULL になるため join は left。
  String HISTORY_SELECT =
      """
      select l.id as id, l.memberCode as memberCode, l.status as status,
             l.linkedAt as linkedAt, lu.displayName as linkedByName,
             l.releasedAt as releasedAt, ru.displayName as releasedByName
      from com.kizuna.customer.domain.CustomerMemberLink l
        left join com.kizuna.user.domain.PlatformUser lu on lu.id = l.linkedBy
        left join com.kizuna.user.domain.PlatformUser ru on ru.id = l.releasedBy
      """;

  String HISTORY_WHERE = " where l.customerId = :customerId ";

  // 新しい区間が先頭。linkedAt の同値は id で決定的に解く。カーソルの比較も同じ組で行う。
  String HISTORY_ORDER = " order by l.linkedAt desc, l.id desc";

  /** 顧客 1 件の紐づけ履歴の先頭。並びは linkedAt の降順に一意な副キー id を重ねて全順序にする。 */
  @Query(HISTORY_SELECT + HISTORY_WHERE + HISTORY_ORDER)
  List<CustomerMemberLinkView> findHistory(@Param("customerId") String customerId, Limit limit);

  /**
   * 顧客 1 件の紐づけ履歴の続き。渡された位置より後ろ（＝より古い側）だけを返す。
   *
   * <p>id は Snowflake の文字列で、比較も文字列の順序で行う。求めるのは全順序の決定性だけなので、 並び（{@link #HISTORY_ORDER}）と同じ順序であれば足りる。
   */
  @Query(
      HISTORY_SELECT
          + HISTORY_WHERE
          + """
            and (l.linkedAt < :cursorLinkedAt
                 or (l.linkedAt = :cursorLinkedAt and l.id < :cursorId))
            """
          + HISTORY_ORDER)
  List<CustomerMemberLinkView> findHistoryAfter(
      @Param("customerId") String customerId,
      @Param("cursorLinkedAt") OffsetDateTime cursorLinkedAt,
      @Param("cursorId") String cursorId,
      Limit limit);
}
