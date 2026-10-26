package com.kizuna.point.domain;

import java.time.LocalDate;

/**
 * 期限ごとの利用可能残高。期限なしは {@code expiresOn} が null で、並びでは末尾に置く。
 *
 * @param expiresOn 有効期限。期限なしは null
 * @param points その期限の未消費量の合計（ロットを跨ぐので long）
 */
public record ExpiryBalance(LocalDate expiresOn, long points) {}
