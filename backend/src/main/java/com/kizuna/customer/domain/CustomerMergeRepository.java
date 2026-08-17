package com.kizuna.customer.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerMergeRepository extends JpaRepository<CustomerMerge, String> {

  /**
   * 統合の付替えで、被統合行に着いていた受注を存続行へ移す。状態（予約中・確定・完了・取消）では絞らない — 移らなかった受注は台帳から辿れない受注になる。
   *
   * <p>受注は order モジュールの集約だが、付替えの主体は統合そのものなのでこの操作は customer 側に置く。型として order へ依存すると order →
   * customer（受注録入が顧客参照を解決する既存の依存）と環になるため、参照は HQL の中だけに閉じる — 読み取りの JOIN が {@code
   * OrderRepository.VIEW_SELECT}（customer を参照）や {@code
   * CustomerMemberLinkRepository#findHistory}（user を参照）で既に取っている形と同じで、綴りの誤りは起動時の HQL 検証で大きく失敗する。
   *
   * <p>WHERE 句に店舗を明示するのは、Hibernate の {@code @Filter} が HQL の一括 UPDATE には掛からないため。フィルタ任せにすると
   * 店舗境界がこの文から消える。
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
      where o.customerId in :customerIds
      group by o.customerId
      """)
  List<CustomerOrderCountView> countOrdersByCustomerId(
      @Param("customerIds") Collection<String> customerIds);

  /** その顧客が統合に関与しているか（存続行として受けた統合・自分が被統合となった統合のいずれも）。 */
  @Query(
      """
      select count(m) > 0 from com.kizuna.customer.domain.CustomerMerge m
      where m.survivingCustomerId = :customerId or m.mergedCustomerId = :customerId
      """)
  boolean existsInvolving(@Param("customerId") String customerId);

  /** ある行が被統合となった統合。統合履歴を両方向で読むうちの片側で、もう一方は存続行として受けた統合。 */
  List<CustomerMerge> findByMergedCustomerId(String mergedCustomerId);
}
