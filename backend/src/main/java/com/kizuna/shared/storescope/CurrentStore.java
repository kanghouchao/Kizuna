package com.kizuna.shared.storescope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * handler 引数に {@code X-Store-ID} で名乗られた店舗の id を受け取る。検証は {@link CurrentStoreArgumentResolver} が行う。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentStore {}
