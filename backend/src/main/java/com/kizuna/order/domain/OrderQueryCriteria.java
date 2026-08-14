package com.kizuna.order.domain;

import java.time.LocalDate;
import java.util.Set;

/**
 * 受注一覧の抽出条件。作業キュー（カーソル）とアーカイブ（オフセット）が同じ条件を共有する — 検索と並びは群を跨いで同じものが当たるため、条件の形が分かれると片方だけが更新される。
 *
 * <p>状態の軸は検索条件に持たせず、この {@code statuses} が担う。画面の側でも群そのものが状態の軸なので、検索欄に状態を置くと 群と条件が食い違う。
 *
 * @param statuses 対象の状態（空にはしない）
 * @param customerName お客様名の部分一致。null なら絞り込まない
 * @param businessDate 営業日の完全一致。null なら絞り込まない
 * @param sortKey 並び替えの鍵
 * @param descending 降順か
 */
public record OrderQueryCriteria(
    Set<OrderStatus> statuses,
    String customerName,
    LocalDate businessDate,
    OrderSortKey sortKey,
    boolean descending) {}
