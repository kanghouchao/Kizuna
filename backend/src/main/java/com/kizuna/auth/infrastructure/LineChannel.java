package com.kizuna.auth.infrastructure;

/**
 * 解決済みの LINE ログインチャネル資格情報。
 *
 * @param channelId チャネル ID（前端にも公開される公開値）
 * @param channelSecret チャネルシークレット（バックエンドから外へ出さない）
 */
public record LineChannel(String channelId, String channelSecret) {}
