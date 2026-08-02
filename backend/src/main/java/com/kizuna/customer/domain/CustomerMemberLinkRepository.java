package com.kizuna.customer.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerMemberLinkRepository extends JpaRepository<CustomerMemberLink, String> {

  Optional<CustomerMemberLink> findByCustomerIdAndStatus(String customerId, LinkStatus status);

  List<CustomerMemberLink> findByCustomerIdInAndStatus(
      Collection<String> customerIds, LinkStatus status);

  boolean existsByMemberIdAndStatus(Long memberId, LinkStatus status);

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
