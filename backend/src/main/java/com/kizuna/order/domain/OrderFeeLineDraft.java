package com.kizuna.order.domain;

/**
 * 明細行の入力。種別と帯符号の金額を運び、行の同一性は持たない — 明細の書き込みは常に差し替えで、行を名指して直す口は無い。
 *
 * <p>名称は {@link OrderFeeLineKind#BASE_COURSE} では使われない（受注のコース名の写しを集約が当てる）。
 */
public record OrderFeeLineDraft(OrderFeeLineKind kind, String name, int amount) {}
