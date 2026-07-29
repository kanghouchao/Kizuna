package com.kizuna.user.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * ロール集約（RBAC）。権限（{@link Permission}）の束であり、担当店舗集合は持たない — 見える範囲はユーザー側の StoreScope が決める。ユーザーへの授与は
 * {@link PlatformUser} 側がロール ID の集合で保持する（跨集約 ID 参照）。
 *
 * <p>平台既定ロール（{@code systemRole=true}）は播種で投入され、名称・権限の変更と削除を拒否する。利用者は自作ロールを API から自由に追加・改廃できる。
 *
 * <p>本ロールは Spring Security の {@code ROLE_*} authority（CAST / MEMBER の本人種別標識）とは別物である。ロール保持者の
 * authority は所持権限を {@code PERM_} 接頭辞で発行したものであり、ロール名は authority に現れない。
 */
@Entity
@Table(name = "t_roles")
@Getter
@NoArgsConstructor
public class Role extends BaseEntity {

  @Column(nullable = false, unique = true, length = 100)
  private String name;

  /** 平台既定ロール。true は名称・権限の変更と削除を拒否する。 */
  @Column(name = "is_system", nullable = false)
  private Boolean systemRole = false;

  // EAGER ElementCollection は既定ではロール 1 行ごとに個別 SELECT を発行する。一覧・ログイン等の
  // 複数ロール取得経路で行数分の副問い合わせが積み上がらないよう、@BatchSize で IN 句によるまとめ取得に切り替える。
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "t_role_permissions", joinColumns = @JoinColumn(name = "role_id"))
  @Column(name = "permission_id", nullable = false)
  @BatchSize(size = 25)
  private Set<Long> permissionIds = new HashSet<>();

  @Builder
  public Role(String name, Boolean systemRole, Set<Long> permissionIds) {
    validateName(name);
    validatePermissions(permissionIds);
    this.name = name;
    this.systemRole = systemRole != null && systemRole;
    this.permissionIds = new HashSet<>(permissionIds);
  }

  /** 名称を変更する。平台既定ロールは拒否する。 */
  public void rename(String name) {
    requireMutable();
    validateName(name);
    this.name = name;
  }

  /** 権限集合を置き換える。平台既定ロールは拒否する。 */
  public void replacePermissions(Set<Long> permissionIds) {
    requireMutable();
    validatePermissions(permissionIds);
    this.permissionIds = new HashSet<>(permissionIds);
  }

  private void requireMutable() {
    if (Boolean.TRUE.equals(this.systemRole)) {
      throw new SystemRoleImmutableException("平台既定ロールは変更できません");
    }
  }

  private static void validateName(String name) {
    if (name == null || name.isBlank()) {
      throw new InvalidRoleException("ロールの名称は必須です");
    }
  }

  private static void validatePermissions(Set<Long> permissionIds) {
    if (permissionIds == null || permissionIds.isEmpty()) {
      throw new InvalidRoleException("ロールには少なくとも 1 つの権限が必要です");
    }
  }
}
