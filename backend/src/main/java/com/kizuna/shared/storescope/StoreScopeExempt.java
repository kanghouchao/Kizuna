package com.kizuna.shared.storescope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 店舗フィルタ（{@code storeFilter} / {@code storeSetFilter}）を有効化しないまま実行する方法であることを宣言する。
 *
 * <p>{@code reason} には代償制御（呼出前の属主検証、推測不能な鍵での指名、跨店舗であること自体が業務要件、等）か、
 * その方法が店舗スコープ表を自ら読まない理由（{@code @StoreScoped} な方法への委譲、等）を一文で書く。既定値は置かない — 理由の無い豁免は作らせない。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StoreScopeExempt {

  String reason();
}
