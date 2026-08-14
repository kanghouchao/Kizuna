package com.kizuna.customer.domain;

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
   * @return 移した受注の件数（統合履歴にそのまま残す）
   */
  @Modifying
  @Query(
      """
      update com.kizuna.order.domain.Order o set o.customerId = :survivingId
      where o.customerId = :mergedId and o.storeId = :storeId
      """)
  int repointOrders(
      @Param("survivingId") String survivingId,
      @Param("mergedId") String mergedId,
      @Param("storeId") Long storeId);

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
