package com.kizuna.user.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 権限（機能権限）の目録行。code は {@link PermissionCode} の enum 名で、播種が唯一の供給経路である（書き込み API を持たない）。行は enum
 * の宣言から播種が導出するため、目録は常に enum の写像である。
 *
 * <p>ロールからは跨集約 ID 参照（{@link Role#getPermissionIds()}）で参照する。
 */
@Entity
@Table(name = "t_permissions")
@Getter
@NoArgsConstructor
public class Permission extends BaseEntity {

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Builder
  public Permission(String code) {
    this.code = code;
  }
}
