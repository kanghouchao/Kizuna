package com.kizuna.user.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface PlatformUserRepository extends CrudRepository<PlatformUser, Long> {
  Optional<PlatformUser> findByEmail(String email);

  List<PlatformUser> findByUserTypeOrderByDisplayNameAsc(UserType userType);

  /**
   * email でユーザーを取得し、行に悲観排他ロック（SELECT ... FOR UPDATE）を掛ける。
   *
   * <p>並行する既存受諾が同一ユーザーの授権店舗集合を read-modify-write する際、ロック取得後に最新状態を読み直させることで 「後着の save
   * が先着の追加を上書きする」取りこぼし（lost update）を防ぐ（#327）。ロックを持たない {@link #findByEmail}
   * とは異なり、呼び出し前に同一エンティティを読み込んでいない前提で新鮮な行を返す。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from PlatformUser u where u.email = :email")
  Optional<PlatformUser> findByEmailForUpdate(@Param("email") String email);
}
