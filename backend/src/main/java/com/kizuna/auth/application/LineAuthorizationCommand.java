package com.kizuna.auth.application;

/** LINE 認可コードの引き渡し（ログインと連携で共通）。code と codeVerifier はそろうとトークンに換えられるため toString から伏せる。 */
public record LineAuthorizationCommand(String code, String redirectUri, String codeVerifier) {

  @Override
  public String toString() {
    return "LineAuthorizationCommand[code=***, redirectUri=" + redirectUri + ", codeVerifier=***]";
  }
}
