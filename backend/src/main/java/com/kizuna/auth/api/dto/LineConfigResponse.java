package com.kizuna.auth.api.dto;

/**
 * GET /platform/line/config の応答。JSON キーは Jackson 設定により snake_case（channel_id）。
 *
 * @param enabled LINE ログインが利用可能か（チャネル ID とシークレットの双方が解決できたときだけ true）
 * @param channelId 前端が認可要求を組み立てるためのチャネル ID。無効時は null（応答から省かれる）
 */
public record LineConfigResponse(boolean enabled, String channelId) {}
