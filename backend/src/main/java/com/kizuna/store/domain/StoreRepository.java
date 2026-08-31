package com.kizuna.store.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

  Page<Store> findByNameContainingIgnoreCaseOrDomainContainingIgnoreCase(
      String name, String domain, Pageable pageable);

  Optional<Store> findByDomain(String domain);

  /**
   * 店舗行を、その店舗の配下に行を建てる間だけ「削除されないように」押さえる（ADR 0016）。
   *
   * <p>配下の行を建てる書き込みは、どのみち外部キー検査で店舗行に key share を要求する。それを配下を押さえる 前に済ませて、順序を上流から下流へ揃えるための口である。
   *
   * <p>native なのは {@code FOR KEY SHARE} が要るため。JPA の {@code PESSIMISTIC_READ} は {@code FOR SHARE} で、
   * 店舗の名称編集まで直列化してしまう。key share が阻むのは店舗の削除だけなので、同時に走る配下の書き込み どうしは待たない。
   */
  @Query(value = "select id from t_stores where id = :id for key share", nativeQuery = true)
  Optional<Long> lockAgainstDeletion(@Param("id") Long id);
}
