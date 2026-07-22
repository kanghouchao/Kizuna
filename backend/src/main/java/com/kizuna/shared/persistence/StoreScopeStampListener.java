package com.kizuna.shared.persistence;

import com.kizuna.shared.storescope.StoreContext;
import jakarta.persistence.PrePersist;
import org.springframework.stereotype.Component;

/**
 * store-scoped エンティティの永続化直前に store_id を機構的に採番する JPA エンティティリスナ（#429）。
 *
 * <p>従来は各サービスの create が「店舗解決 + setStoreId」の四行様板を同型複製していた。 本リスナへ集約し、@StoreScoped の付け忘れを fail-loud
 * で顕在化させる（従来は静默 fail-open）。
 *
 * <ul>
 *   <li>store_id が既に設定済み → 尊重して素通り（CastInvitation の cast 由来 copy、IT の他店舗カナリア直挿を壊さない）。
 *   <li>未設定かつ StoreContext 確立済み → 現店舗 id を採番。
 *   <li>未設定かつ StoreContext 未確立 → {@link IllegalStateException}（@StoreScoped の付け忘れ、または平台側 seam
 *       の未使用）。
 * </ul>
 *
 * <p>Spring Boot は Hibernate へ SpringBeanContainer を登録するため、{@code @EntityListeners} に指定した本クラスは
 * Spring bean として解決され StoreContext が注入される。
 */
@Component
public class StoreScopeStampListener {

  private final StoreContext storeContext;

  public StoreScopeStampListener(StoreContext storeContext) {
    this.storeContext = storeContext;
  }

  @PrePersist
  public void stampStoreId(StoreScopedEntity entity) {
    if (entity.getStoreId() != null) {
      return;
    }
    if (!storeContext.hasStoreId()) {
      throw new IllegalStateException(
          "店舗文脈が確立されていない状態で store-scoped エンティティを永続化しようとしました（@StoreScoped の付け忘れ、"
              + "または平台側 seam の未使用）: "
              + entity.getClass().getName());
    }
    entity.setStoreId(storeContext.getStoreId());
  }
}
