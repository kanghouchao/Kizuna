package com.kizuna.settings.application;

/**
 * LINE ログインチャネル資格情報の型付きスナップショット。キー名（line_channel_id 等）の知識は settings モジュールだけが持ち、
 * 消費側（auth）はこの型のみに依存する。
 *
 * @param channelId チャネル ID（未設定なら空文字）
 * @param channelSecret チャネルシークレット（未設定なら空文字）
 */
public record LineChannelSettings(String channelId, String channelSecret) {

  /** DB に両方が設定されているか（false なら環境変数フォールバックを使う）。 */
  public boolean configured() {
    return channelId != null
        && !channelId.isBlank()
        && channelSecret != null
        && !channelSecret.isBlank();
  }
}
