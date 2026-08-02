package com.kizuna.auth.infrastructure;

import org.springframework.security.core.AuthenticationException;

/**
 * LINE との認可コード交換・id_token 検証に失敗したことを表す例外。
 *
 * <p>{@code AuthenticationException} 系のため 401 で応答され、内部 message はワイヤへ出ない（LINE 側のエラー詳細は攻撃者への情報になるため、
 * ログにだけ残す）。
 */
public class LineAuthenticationException extends AuthenticationException {

  public LineAuthenticationException(String message) {
    super(message);
  }

  public LineAuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
