package com.kizuna.user.domain;

import lombok.Getter;

/**
 * 権限が属する木。グループの木は店舗集合を跨ぐ照会とグループ級データを、店舗の木は現在店舗の業務を開く。一つの権限は一方の木にだけ属し、単店の端点は店舗の木の権限だけを受け付ける（ADR
 * 0042）。
 */
@Getter
public enum PermissionTree {
  GROUP("グループ"),
  STORE("店舗");

  private final String label;

  PermissionTree(String label) {
    this.label = label;
  }
}
