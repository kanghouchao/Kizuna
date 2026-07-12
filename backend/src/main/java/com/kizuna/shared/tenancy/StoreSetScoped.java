package com.kizuna.shared.tenancy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 集合作用域: 付与されたメソッドで Hibernate の {@code storeSetFilter} を有効化する（#323）。
 *
 * <p>認証済み平台トークンの授権店舗集合（{@link StoreScope}）に基づき、SPECIFIC_STORES では {@code tenant_id in (:storeIds)}
 * で行レベルに濾過し、ALL_STORES ではフィルタ無効＝全店可視とする。授権集合を解決できない呼び出しは fail-closed に拒否される。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StoreSetScoped {}
