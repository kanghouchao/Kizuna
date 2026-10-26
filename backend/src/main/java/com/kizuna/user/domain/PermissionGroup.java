package com.kizuna.user.domain;

import lombok.Getter;

/** 権限の業務領域。宣言順がロール編集画面の表示順で、label はサイドバーの節見出しと揃える。 */
@Getter
public enum PermissionGroup {
  BUSINESS("業務"),
  CAST("キャスト"),
  CUSTOMER("顧客"),
  ORGANIZATION("組織"),
  OPERATION("運用");

  private final String label;

  PermissionGroup(String label) {
    this.label = label;
  }
}
