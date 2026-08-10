package com.kizuna.customer.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository
    extends JpaRepository<Customer, String>, JpaSpecificationExecutor<Customer> {

  /**
   * 顧客を悲観排他ロック（SELECT ... FOR UPDATE）付きで取得する。エンティティ名は HQL の予約語衝突を避けるため FQCN で参照する。
   *
   * <p>顧客の紐づけを読み書きする経路の直列化点。紐づけ・変更・解除（{@code CustomerMemberLinkService#link}／{@code
   * CustomerMemberLinkService#unlink}）と、台帳への記帳（受注完了・手動調整）は、いずれも先にこの行を押さえてから紐づけを解決する。
   * 押さえずに解決すると記帳と解除が何も競合せずに双方 commit でき、受注は取り消せないまま、利用と付与だけが解除済みの会員に残る。
   *
   * <p>押さえるのが紐づけ行ではなく顧客行なのは、紐づけ行が置き換わるため。変更は旧行の RELEASED 化と新行の INSERT で成り立つので、
   * 紐づけ行を待つ形では、待たされた問い合わせが置換のコミット後に自分のスナップショットで述語を評価し直す — RELEASED になった旧行は落ち、
   * 置換で入った新行はそのスナップショットに無い。どちらの会員でもない「非会員」として完了してしまい、この結末はどの直列順序にも対応しない。
   * 顧客行なら置換の前後で同一なので、ロック取得後の紐づけ照会は新しい文として走り、READ COMMITTED の既定でも その時点でコミット済みの行（置換後の新しい ACTIVE
   * 行を含む）を必ず見る。
   *
   * <p>会員側の排他はこのロックの射程外。同じ会員を別々の顧客へ紐づける競合は押さえる顧客行が異なるため直列化されず、 防いでいるのは部分一意索引（member_id WHERE
   * status='ACTIVE'）の側で、そこからの一意違反が 409 になる。
   *
   * <p>受注完了は顧客行を引けなくても（行が無い・他店舗で storeFilter に落ちる）そのまま非会員として進む。既にその顧客の紐づけも
   * 引けない状態であり、どの会員にも誤って積まない形なので、この境界は許容する。
   *
   * <p>ロック順序は顧客行 →（紐づけ行の UPDATE／INSERT）→ 台帳の加算ロット（{@code
   * PointEntryRepository#findCreditsForUpdate}）。記帳の 2 経路で順序が揃い、解除は台帳を触らないため待ちが環にならない。
   * 読むだけの経路（残高照会・完了の事前計算・紐づけ履歴・顧客の投影）はこのロックを取らない。
   *
   * <p>Customer は StoreScopedEntity で、JPQL の問い合わせには {@code storeFilter} が掛かる（{@code
   * EntityManager.find} には掛からないため、派生の {@code findById} に @Lock を足す形では店舗境界が外れる）。{@code @StoreScoped}
   * の文脈では 他店舗の顧客が空になり、存在の有無が漏れない既存の性質はこの形で保たれる。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from com.kizuna.customer.domain.Customer c where c.id = :id")
  Optional<Customer> findByIdForUpdate(@Param("id") String id);

  Optional<Customer> findByPhoneNumber(String phoneNumber);

  Optional<Customer> findByPhoneNumberAndStoreId(String phoneNumber, Long storeId);
}
