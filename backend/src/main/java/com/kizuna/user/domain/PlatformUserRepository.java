package com.kizuna.user.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformUserRepository
    extends JpaRepository<PlatformUser, Long>, JpaSpecificationExecutor<PlatformUser> {
  Optional<PlatformUser> findByEmail(String email);

  /**
   * 指定した本人種別で、現店舗を授権する（ALL_STORES または個別授権店舗集合に含む）有効なユーザーを表示名昇順で取得する。店舗スコープの絞り込みを DB
   * 層で行うことで、無関係な他店舗ユーザーの ElementCollection（ロール・店舗集合）を読み込まずに済む。
   *
   * <p>本クエリの WHERE 句は {@link PlatformUser#authorizes} と同じ条件を HQL で再表現しており、呼び出し側は必ず同条件を含む 述語（例:
   * 受付適格判定）でさらに絞り込むこと。本クエリ単体は結果を狭める方向にのみ働く事前絞り込みであり、真の条件は呼び出し側の述語が持つ
   * （二重化した表現が乖離しても、狭める方向にしか作用しないため取りこぼしはあっても漏洩はない）。
   */
  @Query(
      "select u from PlatformUser u where u.userType = :userType and u.enabled = true"
          + " and (u.storeScopeType = com.kizuna.user.domain.StoreScopeType.ALL_STORES"
          + " or :storeId member of u.storeIds)"
          + " order by u.displayName asc")
  List<PlatformUser> findAuthorizedByUserTypeOrderByDisplayNameAsc(
      @Param("userType") UserType userType, @Param("storeId") Long storeId);

  /**
   * email でユーザーを取得し、行に悲観排他ロック（SELECT ... FOR UPDATE）を掛ける。
   *
   * <p>並行する既存受諾が同一ユーザーの授権店舗集合を read-modify-write する際、ロック取得後に最新状態を読み直させることで 「後着の save
   * が先着の追加を上書きする」取りこぼし（lost update）を防ぐ。ロックを持たない {@link #findByEmail}
   * とは異なり、呼び出し前に同一エンティティを読み込んでいない前提で新鮮な行を返す。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from PlatformUser u where u.email = :email")
  Optional<PlatformUser> findByEmailForUpdate(@Param("email") String email);

  /** 指定ロールを授与されたユーザーが 1 人でも存在するか（ロール削除の事前検証）。 */
  @Query(
      "select count(u) > 0 from com.kizuna.user.domain.PlatformUser u"
          + " where :roleId member of u.roleIds")
  boolean existsByRoleId(@Param("roleId") Long roleId);
}
