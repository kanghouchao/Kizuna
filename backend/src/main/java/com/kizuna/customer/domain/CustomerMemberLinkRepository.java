package com.kizuna.customer.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerMemberLinkRepository extends StoreScopedRepository<CustomerMemberLink> {

  List<CustomerMemberLink> findByStoreIdAndCustomerIdOrderByIdAsc(Long storeId, String customerId);

  /**
   * 顧客に紐づく会員（ロックなし）。書き換える経路も同じ問い合わせを使い、直列化は先に取る顧客行のロックが担う（{@code
   * CustomerRepository#findByIdAndStoreIdForUpdate}）。
   */
  Optional<CustomerMemberLink> findByStoreIdAndCustomerIdAndStatus(
      Long storeId, String customerId, LinkStatus status);

  List<CustomerMemberLink> findByStoreIdAndCustomerIdInAndStatus(
      Long storeId, Collection<String> customerIds, LinkStatus status);

  boolean existsByStoreIdAndMemberIdAndStatus(Long storeId, Long memberId, LinkStatus status);

  boolean existsByStoreIdAndCustomerIdAndStatus(Long storeId, String customerId, LinkStatus status);

  /**
   * 統合の付替えで、被統合行の関連を存続行へ移す。状態では絞らない — 解除済み（RELEASED）の区間も移さなければ「過去に誰と紐づいていたか」の 履歴が統合で切れる。
   *
   * <p>顧客参照は行ごと不変の写像（{@code updatable = false}）なので、実体の setter を開かずにここで書く。通常の経路から書き換えられる形にすると、
   * 区間史の顧客が統合以外の理由で動きうることになる。
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

  // 会員起点の照会。店舗文脈を持たない経路（会員本人）からも呼ばれ、引数の storeId が唯一の店舗境界になる。
  // ACTIVE 行は部分一意索引により店舗ごとに 1 件以下。
  Optional<CustomerMemberLink> findByStoreIdAndMemberIdAndStatus(
      Long storeId, Long memberId, LinkStatus status);

  /** 店舗を跨ぐ照会。境界は会員 id — 会員退会が全店舗の ACTIVE 関連を解除するための入口で、店舗条件を持たない。 */
  List<CustomerMemberLink> findByMemberIdAndStatusOrderByIdAsc(Long memberId, LinkStatus status);

  // 実行者の表示名は ID 参照のため JPQL join で取得する。User は FQCN で参照する
  // （HQL の予約語衝突を避ける既存規約）。実行者が削除されると linked_by / released_by は
  // NULL になるため join は left。
  String HISTORY_SELECT =
      """
      select l.id as id, l.memberCode as memberCode, l.status as status,
             l.reason as reason, l.operationReason as operationReason, l.releaseReason as releaseReason,
             l.linkedBy as linkedBy, l.releasedBy as releasedBy,
             l.linkedAt as linkedAt, lu.displayName as linkedByName,
             l.releasedAt as releasedAt, ru.displayName as releasedByName
      from com.kizuna.customer.domain.CustomerMemberLink l
        left join com.kizuna.user.domain.User lu on lu.id = l.linkedBy
        left join com.kizuna.user.domain.User ru on ru.id = l.releasedBy
      """;

  String HISTORY_WHERE = " where l.storeId = :storeId and l.customerId = :customerId ";

  @Query(
      value = HISTORY_SELECT + HISTORY_WHERE,
      countQuery =
          "select count(l) from com.kizuna.customer.domain.CustomerMemberLink l" + HISTORY_WHERE)
  Page<CustomerMemberLinkView> findHistory(
      @Param("storeId") Long storeId, @Param("customerId") String customerId, Pageable pageable);

  // 会員起点の関連一覧（職員の会員コンソール）。境界は会員 id と閲覧者の店舗集合 — 集合内の区間だけを展開し、集合外は
  // 店舗数しか返さないので顧客台帳の行へ店舗集合を跨いでは到達しない。Store / Customer は表示名のためだけに left join する。
  // 返すのは店舗ごとの最新の区間だけ（解除と再紐づけで区間は際限なく増えるが、店舗数は有界）。全区間の履歴は
  // 顧客側の紐づけ履歴（findHistory）が持つ。
  String DIRECTORY_SELECT =
      """
      select l.id as id, l.storeId as storeId, st.name as storeName,
             l.customerId as customerId, c.name as customerName,
             l.status as status, l.reason as reason,
             l.linkedAt as linkedAt, l.releasedAt as releasedAt, l.releaseReason as releaseReason
      from com.kizuna.customer.domain.CustomerMemberLink l
        left join com.kizuna.store.domain.Store st on st.id = l.storeId
        left join com.kizuna.customer.domain.Customer c
          on c.id = l.customerId and c.storeId = l.storeId
      """;

  // 同じ店舗により新しい区間が無い = その店舗の最新の区間。並び（linkedAt, id）と同じ組で新旧を決める。
  String DIRECTORY_LATEST =
      """
       and not exists (select 1 from com.kizuna.customer.domain.CustomerMemberLink n
         where n.memberId = l.memberId and n.storeId = l.storeId
           and (n.linkedAt > l.linkedAt or (n.linkedAt = l.linkedAt and n.id > l.id)))
      """;

  String DIRECTORY_ORDER = " order by l.linkedAt desc, l.id desc";

  /** 全店舗の作用域を持つ閲覧者向け。 */
  @Query(DIRECTORY_SELECT + " where l.memberId = :memberId" + DIRECTORY_LATEST + DIRECTORY_ORDER)
  List<MemberLinkDirectoryView> findLatestDirectoryByMemberId(@Param("memberId") Long memberId);

  /** 個別授権の閲覧者向け。授権店舗の最新の区間だけを展開する。 */
  @Query(
      DIRECTORY_SELECT
          + " where l.memberId = :memberId and l.storeId in :storeIds"
          + DIRECTORY_LATEST
          + DIRECTORY_ORDER)
  List<MemberLinkDirectoryView> findLatestDirectoryByMemberIdAndStoreIdIn(
      @Param("memberId") Long memberId, @Param("storeIds") Collection<Long> storeIds);

  /** 個別授権の閲覧者には見せない、授権外で関連（解除済みを含む）を持つ店舗の数。 */
  @Query(
      "select count(distinct l.storeId) from com.kizuna.customer.domain.CustomerMemberLink l"
          + " where l.memberId = :memberId and l.storeId not in :storeIds")
  long countStoresByMemberIdAndStoreIdNotIn(
      @Param("memberId") Long memberId, @Param("storeIds") Collection<Long> storeIds);
}
