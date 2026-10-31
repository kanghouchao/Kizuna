package com.kizuna.user.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
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
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * グループ共通ユーザー集約。email でログインし、授権は「ロール × 担当店舗集合」で表す。
 *
 * <p>本人種別（{@link UserType}）が {@link UserType#holdsRoles()} のユーザーだけがロールを持ち、CAST / MEMBER
 * は権限モデルに入らない。ロールは跨集約 ID 参照（{@link Role}）。停止は {@link
 * #stop()}（enabled=false）であり、行を削除しないことで過去の実行主体の記録を保持する。
 *
 * <p>不変条件（構築時と再割当時に検証、違反は 400 系ドメイン例外）:
 *
 * <ul>
 *   <li>店舗集合: {@code SPECIFIC_STORES} は非空（MEMBER のみ空を許容 — 登録時点で紐づけ店舗を持たないため）、{@code ALL_STORES}
 *       は空かつ MEMBER には授権不可（{@code authorizes} が無条件 true になる fail-open を塞ぐ）（{@link
 *       InvalidStoreScopeException}）
 *   <li>ロール: STAFF / SERVICE は 1 ロール以上、CAST/MEMBER は空（{@link InvalidRoleGrantException}）
 *   <li>資格情報: SERVICE は email・password・LINE 連携をいずれも持たず、他の種別は email・password が必須（{@link
 *       InvalidCredentialAssignmentException}）
 * </ul>
 */
@Entity
@Table(name = "t_users")
@Getter
@NoArgsConstructor
public class User extends BaseEntity {

  /** ログイン ID を兼ねるメールアドレス。SERVICE のみ null（資格情報を持たないことの構造上の表現）。一意なのは未退会の行の間だけ（ADR 0029）。 */
  @Column(length = 255)
  private String email;

  /** bcrypt ハッシュ。SERVICE のみ null。 */
  @Column private String password;

  /**
   * 資格情報の版（ADR 0022）。パスワード変更・再設定・停止・実効権限の変更で単調に増え、発行済みトークンの claim との相等比較でセッションを即時失効させる。楽観ロックの
   * version とは別物（あちらは無関係な更新でも増える）。
   */
  @Column(name = "credential_version", nullable = false)
  private Long credentialVersion = 0L;

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

  /**
   * 会員退会の終端状態（ADR 0029）。退会した行は email・LINE ID を保持したまま認証・重複確認の候補から外れ、 同じ識別子で新しい身分を作れる。停止（{@link
   * #enabled}）とは別の状態で、不可逆。
   */
  @Column(name = "withdrawn_at")
  private OffsetDateTime withdrawnAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "user_type", nullable = false, length = 20)
  private UserType userType;

  // 2 つの EAGER ElementCollection（本フィールド・storeIds）は既定では
  // 親エンティティ1行ごとに個別 SELECT を発行する。複数ユーザーを一括取得する経路(受付候補一覧等)で
  // 行数分の副問い合わせが積み上がらないよう、@BatchSize で IN 句によるまとめ取得に切り替える。
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "t_user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role_id")
  @BatchSize(size = 25)
  private Set<Long> roleIds = new HashSet<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "store_scope_type", nullable = false, length = 20)
  private StoreScopeType storeScopeType;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "t_user_stores", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "store_id")
  @BatchSize(size = 25)
  private Set<Long> storeIds = new HashSet<>();

  @Builder
  public User(
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
    validateCredentials(userType, email, password, lineUserId);
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

  /**
   * 授権（ロール・担当店舗集合）を再割当てする。構築時と同一の不変条件を検証する（本人属性は変更しない）。ロール集合が変わると版を進める —
   * トークンの権限はロールから発行時に写したものだから。店舗集合は毎要求 DB から解決されるので進めない。{@code CredentialOperations} だけが呼ぶ。
   */
  public void reassignGrants(Set<Long> roleIds, StoreScopeType storeScopeType, Set<Long> storeIds) {
    Set<Long> roles = roleIds == null ? Set.of() : roleIds;
    Set<Long> stores = storeIds == null ? Set.of() : storeIds;
    validateRoleGrant(this.userType, roles);
    validateScope(this.userType, storeScopeType, stores);
    if (!this.roleIds.equals(roles)) {
      this.credentialVersion++;
    }
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

  /** 停止する（enabled=false）。行は削除せず、過去の実行主体の記録を保持する。版も増やし、発行済みセッションを即時失効させる。 */
  public void stop() {
    if (this.enabled) {
      this.enabled = false;
      this.credentialVersion++;
    }
  }

  /** 再開する（enabled=true）。 */
  public void resume() {
    this.enabled = true;
  }

  /** 退会済みにする。識別子の一意性から外れるので、停止と同じく {@code CredentialOperations} だけが呼ぶ。二度目は不変条件違反。 */
  public void markWithdrawn(OffsetDateTime at) {
    if (this.withdrawnAt != null) {
      throw new ConflictException("この利用者は既に退会しています");
    }
    if (at == null) {
      throw new ServiceException("退会日時は必須です");
    }
    this.withdrawnAt = at;
  }

  public boolean isWithdrawn() {
    return withdrawnAt != null;
  }

  /** 発行済みセッションを失効させる（版を 1 つ進める）。本人属性も授権も変えない、失効そのものが目的の操作に使う。 */
  public void invalidateSessions() {
    this.credentialVersion++;
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
    if (this.userType == UserType.SERVICE) {
      throw new InvalidCredentialAssignmentException("SERVICE は LINE を連携できません");
    }
    if (this.lineUserId != null) {
      throw new LineAlreadyLinkedException("このアカウントは既に LINE と連携済みです");
    }
    this.lineUserId = lineUserId;
  }

  /** エンコード済みパスワードで置き換える（呼び出し側で符号化済みであること）。版も増やし、全端末の既存セッションを失効させる。 */
  public void changePassword(String encodedPassword) {
    validateCredentials(this.userType, this.email, encodedPassword, this.lineUserId);
    this.password = encodedPassword;
    this.credentialVersion++;
  }

  private static void validateRoleGrant(UserType userType, Set<Long> roles) {
    if (userType.holdsRoles() && roles.isEmpty()) {
      throw new InvalidRoleGrantException("STAFF / SERVICE には少なくとも 1 つのロールが必要です");
    }
    if (!userType.holdsRoles() && !roles.isEmpty()) {
      throw new InvalidRoleGrantException("CAST / MEMBER にロールを授与できません");
    }
  }

  /**
   * 資格情報の不変条件。SERVICE が資格情報を持たないのは運用規約ではなく構造上の事実であり、対話ログイン不可の第一の防線は 「照合できる値がそもそも行に無い」ことで成り立つ。LINE
   * 連携も対話ログインの同一性根拠なので同列に禁じる。列は NULL 可なので、この検証だけが守衛である。
   */
  private static void validateCredentials(
      UserType userType, String email, String password, String lineUserId) {
    if (userType == UserType.SERVICE) {
      if (email != null || password != null || lineUserId != null) {
        throw new InvalidCredentialAssignmentException("SERVICE は email・パスワード・LINE 連携を持てません");
      }
      return;
    }
    if (email == null || password == null) {
      throw new InvalidCredentialAssignmentException("email・パスワードは必須です");
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
