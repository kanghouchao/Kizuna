package com.kizuna.order.application;

import com.kizuna.order.domain.OrderFeeLineSnapshot;
import com.kizuna.order.domain.OrderView;
import java.util.List;

/**
 * 受注 1 件の詳細。表示名の join を持つ読み側 projection に、集約から取った行の集合を添える。
 *
 * <p>集約の遅延読み込みはトランザクションの外へ持ち出せないので、明細は不変な写しに取り出してある。
 */
public record OrderDetail(
    OrderView view,
    List<OrderFeeLineSnapshot> feeLines,
    int totalRemuneration,
    int totalDurationMinutes,
    List<BusinessContactPermissionState> businessContactPermissions,
    List<SpecialServiceStatus> specialServices) {}
