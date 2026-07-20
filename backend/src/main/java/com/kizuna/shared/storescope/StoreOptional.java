package com.kizuna.shared.storescope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 店舗文脈が解決できなくても処理を許可するエンドポイントにのみ付与するマーカーアノテーション。
 *
 * <p>{@link StoreIdInterceptor} は、JWT の storeId claim でもヘッダでも店舗を解決できなかったリクエストを既定で 403
 * として拒否する（fail-closed）。このアノテーションが付与されたハンドラメソッドに限り、店舗文脈が無いまま処理の継続を許可する。
 *
 * <p>付与してよいのは、店舗文脈をそもそも参照しない、または文脈をリクエストボディ等から独自に解決するエンドポイントのみ。 店舗分離の抜け穴になり得るため、安易に付与しないこと。
 *
 * @see StoreIdInterceptor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StoreOptional {}
