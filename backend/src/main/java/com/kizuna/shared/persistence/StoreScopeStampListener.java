package com.kizuna.shared.persistence;

import jakarta.persistence.PrePersist;

/**
 * store-scoped エンティティの永続化直前に store_id の設定を検証する JPA エンティティリスナ。値は補わない — 生成箇所が店舗 id
 * を明示的に設定しなければならず、未設定は {@link IllegalStateException} で拒否する。
 */
public class StoreScopeStampListener {

  @PrePersist
  public void requireStoreId(StoreScopedEntity entity) {
    if (entity.getStoreId() == null) {
      throw new IllegalStateException(
          "store_id が未設定の store-scoped エンティティを永続化しようとしました: " + entity.getClass().getName());
    }
  }
}
