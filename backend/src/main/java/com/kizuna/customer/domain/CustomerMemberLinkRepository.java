package com.kizuna.customer.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerMemberLinkRepository extends JpaRepository<CustomerMemberLink, String> {

  Optional<CustomerMemberLink> findByCustomerIdAndStatus(String customerId, LinkStatus status);

  /**
   * 顧客の紐づけを悲観排他ロック（SELECT ... FOR UPDATE）付きで取得する。エンティティ名は HQL の予約語衝突を避けるため FQCN で参照する。
   *
   * <p>記帳の経路（受注完了・手動調整）は、積み先の会員を決めた行をコミットまで押さえる。押さえないと並行する解除とは何も競合せず、 双方が commit できてしまう —
   * 受注は取り消せないまま完了し、利用と付与だけが解除済みの会員に残る。
   *
   * <p>解除の側に明示のロックは要らない。記帳が先に取れば、解除の UPDATE（flush の時期に関わらずコミット前に必ず走る）が
   * この行ロックの解放を待つ。解除が先なら、記帳側のロック待ちは解除のコミット後に述語を評価し直し（READ COMMITTED の既定）、 RELEASED
   * になった行はこの問い合わせに掛からず、記帳は非会員として扱う。
   *
   * <p>守れるのは「今 ACTIVE な行がある」場合まで。行が無ければ押さえる対象も無いので、並行して commit される新しい紐づけとは
   * 競合せず、記帳は紐づけ無しとして進む。どの会員にも誤って積まない形なので、この境界は許容する。
   *
   * <p>ロック順序は台帳の加算ロット（{@code PointEntryRepository#findCreditsForUpdate}）より先。記帳の 2 経路で順序が揃い、
   * 解除は台帳を触らないため、待ちが環にならない。
   *
   * <p>読むだけの経路（残高照会・完了の事前計算・顧客の投影）は {@link #findByCustomerIdAndStatus} を使う。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select l from com.kizuna.customer.domain.CustomerMemberLink l"
          + " where l.customerId = :customerId and l.status = :status")
  Optional<CustomerMemberLink> findByCustomerIdAndStatusForUpdate(
      @Param("customerId") String customerId, @Param("status") LinkStatus status);

  List<CustomerMemberLink> findByCustomerIdInAndStatus(
      Collection<String> customerIds, LinkStatus status);

  boolean existsByMemberIdAndStatus(Long memberId, LinkStatus status);

  // 会員起点の照会。店舗文脈を持たない経路（会員本人）から呼ばれるため storeFilter は働かず、
  // 引数の storeId が唯一の店舗境界になる。ACTIVE 行は部分一意索引により店舗ごとに 1 件以下。
  Optional<CustomerMemberLink> findByStoreIdAndMemberIdAndStatus(
      Long storeId, Long memberId, LinkStatus status);

  // 実行者の表示名は ID 参照のため JPQL join で取得する。PlatformUser は FQCN で参照する
  // （HQL の予約語衝突を避ける既存規約）。実行者が削除されると linked_by / released_by は
  // NULL になるため join は left。
  // 新しい区間が先頭。linkedAt の同値は Snowflake id（時刻順）で決定的に解く。
  @Query(
      """
      select l.id as id, l.memberCode as memberCode, l.status as status,
             l.linkedAt as linkedAt, lu.displayName as linkedByName,
             l.releasedAt as releasedAt, ru.displayName as releasedByName
      from com.kizuna.customer.domain.CustomerMemberLink l
        left join com.kizuna.user.domain.PlatformUser lu on lu.id = l.linkedBy
        left join com.kizuna.user.domain.PlatformUser ru on ru.id = l.releasedBy
      where l.customerId = :customerId
      order by l.linkedAt desc, l.id desc
      """)
  List<CustomerMemberLinkView> findHistory(@Param("customerId") String customerId);
}
