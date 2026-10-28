package com.kizuna.order.application;

/** 巻き戻しの実行内容。請求と相殺額は画面が下見で確認した値で、現物とずれていれば実行しない。 */
public record OrderPointRollbackCommand(
    Integer expectedTotalFee, Integer expectedOffsetAmount, String reason) {}
