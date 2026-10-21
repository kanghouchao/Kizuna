package com.kizuna.user.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long> {

  Optional<PlatformUser> findByUserId(Long userId);

  List<PlatformUser> findByUserIdIn(Collection<Long> userIds);

  /**
   * 認証主体（email）の店舗作用域を 1 回の SELECT で引く。要求ごとの解決器が呼ぶ経路なので、ユーザー行を別に読まず 店舗集合も結合で同時に取る（EAGER の既定だと実体と集合で
   * 2 回の SELECT になる）。
   */
  @Query(
      "select distinct p from com.kizuna.user.domain.PlatformUser p"
          + " left join fetch p.storeIds"
          + " where p.userId = (select u.id from com.kizuna.user.domain.User u"
          + " where u.email = :email)")
  Optional<PlatformUser> findByUserEmail(@Param("email") String email);
}
