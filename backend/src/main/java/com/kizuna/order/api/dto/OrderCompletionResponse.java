package com.kizuna.order.api.dto;

/**
 * 完了処理の応答。伝票トークンの生値だけを持つ専用の型で、会員へ帰属した完了では null（応答からキーごと消える）。
 *
 * <p>生値を持つ型をこの 1 つに閉じるのは、保存されるのがダイジェストだけで、この応答を取り逃すと二度と手に入らないためである。 一覧や詳細と型を共有すると、抑制の書き漏らし 1
 * 箇所がそのまま漏出になる。
 */
public record OrderCompletionResponse(String receiptToken) {}
