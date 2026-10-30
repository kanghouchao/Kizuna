package com.kizuna.shared.persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** 実 PostgreSQL テスト層の印。接続先が与えられた実行（task test:postgres）でだけ動き、通常の単体テストでは飛ばす。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnabledIfEnvironmentVariable(named = PostgresTestDatabase.URL_VARIABLE, matches = ".+")
public @interface EnabledIfPostgres {}
