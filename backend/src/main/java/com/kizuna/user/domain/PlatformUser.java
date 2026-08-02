package com.kizuna.user.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * プラットフォーム共通ユーザー集約。email でログインし、授権は「ロール × 担当店舗集合」で表す。
 *
 * <p>本人種別（{@link UserType}）が STAFF のユーザーだけがロールを持ち、CAST / MEMBER は権限モデルに入らない（既定）。ロールは跨集約 ID 参照
 * （{@link Role}）。停止は {@link #stop()}（enabled=false）であり、行を削除しないことで過去の実行主体の記録を保持する。
 *
 * <p>不変条件（構築時と再割当時に検証、違反は 400 系ドメイン例外）:
 *
 * <ul>
 *   <li>店舗集合: {@code SPECIFIC_STORES} は非空（MEMBER のみ空を許容 — 登録時点で紐づけ店舗を持たないため）、{@code ALL_STORES}
 *       は空かつ MEMBER には授権不可（{@code authorizes} が無条件 true になる fail-open を塞ぐ）（{@link
 *       InvalidStoreScopeException}）
 *   <li>ロール: STAFF は 1 ロール以上、CAST/MEMBER は空（{@link InvalidRoleGrantException}）
 * </ul>
 */
@Entity
@Table(name = "t_users")
@Getter
@NoArgsConstructor
public class PlatformUser extends BaseEntity {

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(nullable = false)
  private String password;

  @Column(name = "display_name", nullable = false, length = 150)
  private String displayName;

  /**
   * 連携済み LINE ユーザー ID（未連携は null）。LINE ログインはこの値だけを同一性の根拠にし、メール一致による自動連携は行わない （LINE
   * 側の検証済みメールが既存アカウントと一致しても、それだけでは本人性の証明にならないため）。
   */
  @Column(name = "line_user_id", length = 64)
  private String lineUserId;

  @Column(nullable = false)
  private Boolean enabled = true;

  @Enumerated(EnumType.STRING)
  @Column(name = "user_type", nullable = false, length = 20)
  private UserType userType;

  // 2 つの EAGER ElementCollection（本フィールド・storeIds）は既定では
  // 親エンティティ1行ごとに個別 SELECT を発行する。複数ユーザーを一括取得する経路(受付候補一覧等)で
  // 行数分の副問い合わせが積み上がらないよう、@BatchSize で IN 句によるまとめ取得に切り替える。
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "t_user_roles", joinColumns = @JoinColumn(name = "platform_user_id"))
  @Column(name = "role_id")
  @BatchSize(size = 25)
  private Set<Long> roleIds = new HashSet<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "store_scope_type", nullable = false, length = 20)
  private StoreScopeType storeScopeType;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "t_user_stores", joinColumns = @JoinColumn(name = "platform_user_id"))
  @Column(name = "store_id")
  @BatchSize(size = 25)
  private Set<Long> storeIds = new HashSet<>();

  @Builder
  public PlatformUser(
      String email,
      String password,
      String displayName,
      boolean enabled,
      UserType userType,
      Set<Long> roleIds,
      StoreScopeType storeScopeType,
      Set<Long> storeIds,
      String lineUserId) {
    Set<Long> roles = roleIds == null ? Set.of() : roleIds;
    Set<Long> stores = storeIds == null ? Set.of() : storeIds;
    validateRoleGrant(userType, roles);
    validateScope(userType, storeScopeType, stores);
    this.email = email == null ? null : email.toLowerCase(Locale.ROOT);
    this.password = password;
    this.displayName = displayName;
    this.lineUserId = lineUserId;
    this.enabled = enabled;
    this.userType = userType;
    this.roleIds = new HashSet<>(roles);
    this.storeScopeType = storeScopeType;
    this.storeIds = new HashSet<>(stores);
  }

  /** 授権（ロール・担当店舗集合）を再割当てする。構築時と同一の不変条件を検証する（本人属性は変更しない）。 */
  public void reassignGrants(Set<Long> roleIds, StoreScopeType storeScopeType, Set<Long> storeIds) {
    Set<Long> roles = roleIds == null ? Set.of() : roleIds;
    Set<Long> stores = storeIds == null ? Set.of() : storeIds;
    validateRoleGrant(this.userType, roles);
    validateScope(this.userType, storeScopeType, stores);
    this.roleIds = new HashSet<>(roles);
    this.storeScopeType = storeScopeType;
    this.storeIds = new HashSet<>(stores);
  }

  /** 担当店舗集合のみを再割当てする（CAST の招待受諾が所属店舗を冪等 union する用途。ロールは変更しない）。 */
  public void reassignStores(StoreScopeType storeScopeType, Set<Long> storeIds) {
    Set<Long> stores = storeIds == null ? Set.of() : storeIds;
    validateScope(this.userType, storeScopeType, stores);
    this.storeScopeType = storeScopeType;
    this.storeIds = new HashSet<>(stores);
  }

  /** 停止する（enabled=false）。行は削除せず、過去の実行主体の記録を保持する。 */
  public void stop() {
    this.enabled = false;
  }

  /** 再開する（enabled=true）。 */
  public void resume() {
    this.enabled = true;
  }

  /** 表示名を更新する（自己プロフィール更新用。不変条件なし）。 */
  public void updateDisplayName(String displayName) {
    this.displayName = displayName;
  }

  /**
   * LINE アカウントを連携する。連携は 1 身分につき 1 度きりで、解除も付け替えも提供しない。
   *
   * @throws LineAlreadyLinkedException 既に別の LINE アカウントを連携済みの場合
   */
  public void linkLine(String lineUserId) {
    if (this.lineUserId != null) {
      throw new LineAlreadyLinkedException("このアカウントは既に LINE と連携済みです");
    }
    this.lineUserId = lineUserId;
  }

  /** エンコード済みパスワードで置き換える（呼び出し側で符号化済みであること）。 */
  public void changePassword(String encodedPassword) {
    this.password = encodedPassword;
  }

  private static void validateRoleGrant(UserType userType, Set<Long> roles) {
    if (userType == UserType.STAFF && roles.isEmpty()) {
      throw new InvalidRoleGrantException("STAFF には少なくとも 1 つのロールが必要です");
    }
    if (userType != UserType.STAFF && !roles.isEmpty()) {
      throw new InvalidRoleGrantException("CAST / MEMBER にロールを授与できません");
    }
  }

  private static void validateScope(
      UserType userType, StoreScopeType storeScopeType, Set<Long> stores) {
    if (userType == UserType.MEMBER && storeScopeType == StoreScopeType.ALL_STORES) {
      throw new InvalidStoreScopeException("MEMBER に ALL_STORES を授権できません");
    }
    if (storeScopeType == StoreScopeType.SPECIFIC_STORES
        && stores.isEmpty()
        && userType != UserType.MEMBER) {
      throw new InvalidStoreScopeException("SPECIFIC_STORES の授権には少なくとも 1 つの店舗が必要です");
    }
    if (storeScopeType == StoreScopeType.ALL_STORES && !stores.isEmpty()) {
      throw new InvalidStoreScopeException("ALL_STORES の授権に個別店舗を指定できません");
    }
  }

  /** 指定店舗を授権するか。ALL_STORES は常に true、SPECIFIC_STORES は店舗集合のメンバーのみ true。 */
  public boolean authorizes(Long storeId) {
    return storeScopeType == StoreScopeType.ALL_STORES || storeIds.contains(storeId);
  }
}
