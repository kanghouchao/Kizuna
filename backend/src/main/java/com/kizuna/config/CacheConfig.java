package com.kizuna.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * キャッシュ設定クラス。
 *
 * <p>デフォルトでは ConcurrentMapCacheManager（メモリキャッシュ）を使用。 必要に応じて Redis キャッシュに切り替え可能。
 */
@Configuration
@EnableCaching
public class CacheConfig {}
