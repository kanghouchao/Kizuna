package com.kizuna.order.domain;

/** 月次報酬の照会対象キャスト 1 行（読み側 projection）。 */
public record RemunerationCastRow(Long personId, String name) {}
