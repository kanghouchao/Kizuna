package com.kizuna.user.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
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

  /** 連携済み LINE ユーザー ID で身分を引く。LINE ログインが同一性の根拠にする唯一の経路。 */
  Optional<PlatformUser> findByLineUserId(String lineUserId);

  /** 指定 LINE ユーザー ID が既に別の身分へ連携済みか（連携要求の事前検証）。 */
  boolean existsByLineUserId(String lineUserId);

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

  /**
   * id でユーザーを取得し、行に悲観排他ロック（SELECT ... FOR UPDATE）を掛ける。
   *
   * <p>店長の任命・解任はロール集合と授権店舗集合の read-modify-write だが、要求は版を運ばない（画面に編集フォームが無い）。
   * 版の照合で撥ねると利用者に再試行の手立てが無いため、直列化して待たせる。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from PlatformUser u where u.id = :id")
  Optional<PlatformUser> findByIdForUpdate(@Param("id") Long id);

  /**
   * 本人種別 STAFF の行に限り email を返す（アカウント面の対象絞りと自己停止判定の事前検査）。
   *
   * <p>実体でなく列を返す。実体を読み込んだうえで同じ行を {@link #findByIdForUpdate} で押さえるとロックの昇格が版の照合を伴い、
   * 直列化点で待っている間に他者が停止を確定させただけで 409 に落ちる — 冪等であるべき停止がそこで壊れる。
   */
  @Query(
      "select u.email from PlatformUser u where u.id = :id"
          + " and u.userType = com.kizuna.user.domain.UserType.STAFF")
  Optional<String> findStaffEmailById(@Param("id") Long id);

  /**
   * 指定ロールのいずれかを保持する有効な利用者の行を、id 昇順で押さえる（{@code SELECT ... FOR UPDATE}）。
   *
   * <p><b>返る集合を数えてはいけない</b>。READ COMMITTED では待たされている間に確定した変更のうち、取り直されるのは押さえた行自身の 列だけで、保持判定が読む
   * t_user_roles は元のスナップショットのまま — 降格済みの相手が保持者に見え続ける（実測: {@code
   * PlatformStaffManagementIT#lockedLookupIsStaleSoTheCountMustBeTakenAgain}）。数えるのは {@link
   * #findEnabledRoleHolderIds} で押さえた後に取り直す。
   *
   * <p>母集団の全行を押さえるのは、計数だけでは最後の 2 人が同時に相互降級したとき双方が検査を通り 0 になるため（ADR 0020）。id
   * 昇順は獲得順序を揃えて待ちを環にしない。実体でなく id を返すのは、実体で受けるとロックの獲得が版の照合を伴い、 書きもしない他人の行の版が進んだだけで授権の更新が 409 に落ちるため。
   *
   * <p>母集団はログインして管理面を行使できる STAFF に限る。SERVICE もロールを持つが対話ログインできず、 最後の管理権限保持者としては数えられない。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select u.id from PlatformUser u where u.enabled = true"
          + " and u.userType = com.kizuna.user.domain.UserType.STAFF"
          + " and exists (select 1 from PlatformUser h join h.roleIds rid"
          + " where h.id = u.id and rid in :roleIds)"
          + " order by u.id")
  List<Long> lockEnabledRoleHolderIds(@Param("roleIds") Collection<Long> roleIds);

  /** 同じ母集団を押さえずに読む。{@link #lockEnabledRoleHolderIds} の後に呼ぶと、新しいスナップショットで実際の顔ぶれが返る。 */
  @Query(
      "select u.id from PlatformUser u where u.enabled = true"
          + " and u.userType = com.kizuna.user.domain.UserType.STAFF"
          + " and exists (select 1 from PlatformUser h join h.roleIds rid"
          + " where h.id = u.id and rid in :roleIds)")
  List<Long> findEnabledRoleHolderIds(@Param("roleIds") Collection<Long> roleIds);

  /**
   * 資格情報の版だけをスカラー投影で返す（版キャッシュの miss 時の埋め戻し用）。実体を読まないのは、認証フィルタの 毎要求経路で
   * ElementCollection（ロール・店舗集合）まで読み込む無駄を避けるため。
   */
  @Query("select u.credentialVersion from PlatformUser u where u.email = :email")
  Optional<Long> findCredentialVersionByEmail(@Param("email") String email);

  /** 指定ロールを授与されたユーザーが 1 人でも存在するか（ロール削除の事前検証）。 */
  @Query(
      "select count(u) > 0 from com.kizuna.user.domain.PlatformUser u"
          + " where :roleId member of u.roleIds")
  boolean existsByRoleId(@Param("roleId") Long roleId);
}
