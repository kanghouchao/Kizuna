package com.kizuna.customer.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerMergeRepository extends StoreScopedRepository<CustomerMerge> {

  interface OrderState {
    String getId();

    String getCustomerId();

    String getStatus();

    Long getVersion();
  }

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select o.id as id, o.customerId as customerId, o.status as status, o.version as version from com.kizuna.order.domain.Order o where o.storeId = :storeId and o.customerId in :ids order by o.id")
  List<OrderState> lockOrders(Long storeId, Collection<String> ids);

  /**
   * 統合の付替えで、被統合行に着いていた受注を存続行へ移す。状態（予約中・確定・完了・取消）では絞らない — 移らなかった受注は台帳から辿れない受注になる。
   *
   * <p>受注は order モジュールの集約だが、付替えの主体は統合そのものなのでこの操作は customer 側に置く。型として order へ依存すると order →
   * customer（受注録入が顧客参照を解決する既存の依存）と環になるため、参照は HQL の中だけに閉じる — 読み取りの JOIN が {@code
   * OrderRepository.VIEW_SELECT}（customer を参照）や {@code
   * CustomerMemberLinkRepository#findHistory}（user を参照）で既に取っている形と同じで、綴りの誤りは起動時の HQL 検証で大きく失敗する。
   *
   * <p>{@code versioned} を付けるのは、受注の顧客参照が可変列であり、この文が版を上げないと並行する受注更新の
   * 書き戻しが付替えを静かに取り消すため。付替えの前に読まれた受注は、自分が読んだ時点の顧客（墓標）を他の項目と 一緒に flush する — 版が据え置きなら楽観ロックの述語が成立し、その 1
   * 件だけ墓標に着いたまま残る。 版を上げれば敗者は 409 になり、やり直せば正しい存続行に着く。
   *
   * @return 移した受注の件数（統合履歴にそのまま残す）
   */
  @Modifying
  @Query(
      """
      update versioned com.kizuna.order.domain.Order o set o.customerId = :survivingId
      where o.customerId = :mergedId and o.storeId = :storeId
      """)
  int repointOrders(
      @Param("survivingId") String survivingId,
      @Param("mergedId") String mergedId,
      @Param("storeId") Long storeId);

  /**
   * 顧客ごとの受注件数。重複候補を見比べる人が「どちらの行に来店の記録が積まれているか」を判断する材料で、状態では絞らない — 統合が移すのも全状態である。
   *
   * <p>受注を参照する理由と、型ではなく HQL の中だけで名指す理由は {@link #repointOrders} と同じ。行ごとに数えず 1 文でまとめて引くのは、
   * 候補が数十行になっても問い合わせが増えないようにするため。受注を 1 件も持たない顧客はこの結果に現れない（呼出側が 0 として扱う）。
   */
  @Query(
      """
      select o.customerId as customerId, count(o) as orderCount
      from com.kizuna.order.domain.Order o
      where o.storeId = :storeId and o.customerId in :customerIds
      group by o.customerId
      """)
  List<CustomerOrderCountView> countOrdersByCustomerId(
      @Param("storeId") Long storeId, @Param("customerIds") Collection<String> customerIds);

  /** その顧客が統合に関与しているか（存続行として受けた統合・自分が被統合となった統合のいずれも）。 */
  @Query(
      """
      select count(m) > 0 from com.kizuna.customer.domain.CustomerMerge m
      where m.storeId = :storeId
        and (m.survivingCustomerId = :customerId or m.mergedCustomerId = :customerId)
      """)
  boolean existsInvolving(@Param("storeId") Long storeId, @Param("customerId") String customerId);

  // 一覧は現在の表示名を参照する。実行者削除後も履歴を返すため left join にする。
  // 統合時点の表示名と原資料は変更不能な evidence に保持する。
  String HISTORY_SELECT =
      """
      select m.id as id,
             m.survivingCustomerId as survivingCustomerId, sc.name as survivingCustomerName,
             m.mergedCustomerId as mergedCustomerId, mc.name as mergedCustomerName,
             mu.displayName as mergedByName, m.mergedAt as mergedAt,
             m.movedOrderCount as movedOrderCount, m.movedLinkCount as movedLinkCount,
             m.movedContactCount as movedContactCount, m.operationReason as operationReason
      from com.kizuna.customer.domain.CustomerMerge m
        left join com.kizuna.customer.domain.Customer sc
          on sc.id = m.survivingCustomerId and sc.storeId = m.storeId
        left join com.kizuna.customer.domain.Customer mc
          on mc.id = m.mergedCustomerId and mc.storeId = m.storeId
        left join com.kizuna.user.domain.User mu on mu.id = m.mergedBy
      """;

  // 両方向を 1 文で引く。括弧は必須 — 外すと店舗条件が `or` の片方の枝にしか掛からず、
  // 他店舗の統合が混ざる。
  String HISTORY_WHERE =
      " where m.storeId = :storeId"
          + " and (m.survivingCustomerId = :customerId or m.mergedCustomerId = :customerId) ";

  @Query(
      value = HISTORY_SELECT + HISTORY_WHERE,
      countQuery =
          "select count(m) from com.kizuna.customer.domain.CustomerMerge m" + HISTORY_WHERE)
  Page<CustomerMergeView> findHistory(
      @Param("storeId") Long storeId, @Param("customerId") String customerId, Pageable pageable);
}
