package com.kizuna.store.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

  @Query(value = "select id from t_stores where id = :id for no key update", nativeQuery = true)
  Optional<Long> lockCastFields(@Param("id") Long id);

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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Store s where s.id = :id")
  Optional<Store> findByIdForUpdate(@Param("id") Long id);

  /** 店舗の物理削除で外部キーの連鎖により消える行の表名と件数。行の内容は読まない。 */
  interface CascadeCount {
    String getKind();

    Long getRows();
  }

  /**
   * {@code t_stores} を ON DELETE CASCADE で参照する表を DB のカタログから引き、店舗ごとの件数を数える。
   * 表を列挙し直さないので、連鎖先が増えても数え漏れない。
   */
  @Query(
      value =
          """
          select c.conrelid::regclass::text as kind,
                 (xpath('/row/n/text()',
                    query_to_xml(
                      format('select count(*) as n from %s where %I = %s',
                             c.conrelid::regclass, a.attname, cast(:id as bigint)),
                      false, true, '')))[1]::text::bigint as rows
            from pg_constraint c
            join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
           where c.contype = 'f'
             and c.confrelid = 't_stores'::regclass
             and c.confdeltype = 'c'
           order by 1
          """,
      nativeQuery = true)
  List<CascadeCount> countCascade(@Param("id") Long id);
}
