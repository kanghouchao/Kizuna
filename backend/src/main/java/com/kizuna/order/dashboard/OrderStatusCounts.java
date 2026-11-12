package com.kizuna.order.dashboard;

/** 受注の状態別の件数。誤完了の無効化を受けた完了は提供の無かった受注なので、完了に数えない。 */
public record OrderStatusCounts(long confirmed, long inService, long completed, long cancelled) {}
