package com.kizuna.shared.tenancy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * テナント文脈が解決できなくても処理を許可するエンドポイントにのみ付与するマーカーアノテーション。
 *
 * <p>{@link TenantIdInterceptor} は、JWT の tenantId claim でもヘッダでもテナントを解決できなかったリクエストを既定で 403
 * として拒否する（fail-closed）。このアノテーションが付与されたハンドラメソッドに限り、テナント文脈が無いまま処理の継続を許可する。
 *
 * <p>付与してよいのは、テナント文脈をそもそも参照しない、または文脈をリクエストボディ等から独自に解決するエンドポイントのみ。 テナント分離の抜け穴になり得るため、安易に付与しないこと。
 *
 * @see TenantIdInterceptor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantOptional {}
