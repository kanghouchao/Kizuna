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
 * プラットフォーム共通ユーザー集約。email でログインし、資格情報・本人種別・ロール集合を持つ。担当店舗集合は {@link PlatformUser}
 * が別に持つ（トークンに載せず要求ごとに解決するため）。
 *
 * <p>本人種別（{@link UserType}）が {@link UserType#holdsRoles()} のユーザーだけがロールを持ち、CAST / MEMBER
 * は権限モデルに入らない。ロールは跨集約 ID 参照（{@link Role}）。停止は {@link
 * #stop()}（enabled=false）であり、行を削除しないことで過去の実行主体の記録を保持する。
 *
 * <p>不変条件（構築時と再割当時に検証、違反は 400 系ドメイン例外）:
 *
 * <ul>
 *   <li>ロール: PLATFORM_USER / SERVICE は 1 ロール以上、CAST/MEMBER は空（{@link InvalidRoleGrantException}）
 *   <li>資格情報: SERVICE は email・password・LINE 連携をいずれも持たず、他の種別は email・password が必須（{@link
 *       InvalidCredentialAssignmentException}）
 * </ul>
 */
@Entity
@Table(name = "t_users")
@Getter
@NoArgsConstructor
public class User extends BaseEntity {

  /** ログイン ID を兼ねるメールアドレス。SERVICE のみ null（資格情報を持たないことの構造上の表現）。 */
  @Column(unique = true, length = 255)
  private String email;

  /** bcrypt ハッシュ。SERVICE のみ null。 */
  @Column private String password;

  /**
   * 資格情報の版（ADR 0022）。パスワード変更・再設定・停止で単調に増え、発行済みトークンの claim との 相等比較でセッションを即時失効させる。楽観ロックの version
   * とは別物（あちらは無関係な更新でも増える）。
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

  @Enumerated(EnumType.STRING)
  @Column(name = "user_type", nullable = false, length = 20)
  private UserType userType;

  // EAGER ElementCollection は既定では親エンティティ 1 行ごとに個別 SELECT を発行する。複数ユーザーを
  // 一括取得する経路(受付候補一覧等)で行数分の副問い合わせが積み上がらないよう、@BatchSize で IN 句による
  // まとめ取得に切り替える。
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "t_user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role_id")
  @BatchSize(size = 25)
  private Set<Long> roleIds = new HashSet<>();

  @Builder
  public User(
      String email,
      String password,
      String displayName,
      boolean enabled,
      UserType userType,
      Set<Long> roleIds,
      String lineUserId) {
    Set<Long> roles = roleIds == null ? Set.of() : roleIds;
    validateRoleGrant(userType, roles);
    validateCredentials(userType, email, password, lineUserId);
    this.email = email == null ? null : email.toLowerCase(Locale.ROOT);
    this.password = password;
    this.displayName = displayName;
    this.lineUserId = lineUserId;
    this.enabled = enabled;
    this.userType = userType;
    this.roleIds = new HashSet<>(roles);
  }

  /**
   * ロール集合を再割当てする。構築時と同一の不変条件を検証する（本人属性は変更しない）。
   *
   * <p>同じ内容でも集合を必ず新しいインスタンスへ置き換える。集合の差し替えで版が進み、授権編集面の陳腐化検査（応答で往復した version
   * の照合）が店舗集合だけの変更でも効くようにするため — 店舗集合は別集約で、この行だけでは版が進まない。
   */
  public void reassignRoles(Set<Long> roleIds) {
    Set<Long> roles = roleIds == null ? Set.of() : roleIds;
    validateRoleGrant(this.userType, roles);
    this.roleIds = new HashSet<>(roles);
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
      throw new InvalidRoleGrantException("PLATFORM_USER / SERVICE には少なくとも 1 つのロールが必要です");
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
}
