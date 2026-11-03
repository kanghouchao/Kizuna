package com.kizuna.auth.application;

/** LINE ログインの公開設定。無効なら channelId は null。 */
public record LineConfig(boolean enabled, String channelId) {}
