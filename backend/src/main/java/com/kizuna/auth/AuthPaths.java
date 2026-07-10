package com.kizuna.auth;

/**
 * auth モジュールが公開する API パス定数。
 *
 * <p>{@code com.kizuna.auth} ベースパッケージ直下に置くことで、Spring Modulith の既定規則により auth モジュールの公開 API となり、CSRF
 * 除外設定・招待メールリンク生成など他モジュールからも同一のパスを参照できる（綴り違いの再発を防ぐ）。
 */
public final class AuthPaths {

  private AuthPaths() {}

  /** 管理者ユーザー初期化エンドポイントの、{@code AuthController} クラスパス（{@code /tenant}）配下の相対パス。 */
  public static final String INIT_ADMIN_USER = "/init-admin-user";

  /**
   * 管理者ユーザー初期化エンドポイントの絶対パス（CSRF 除外・招待メールリンク生成が参照）。 先頭の {@code /tenant} は {@code AuthController}
   * のクラスレベル {@code @RequestMapping} と一致させること。
   */
  public static final String INIT_ADMIN_USER_PATH = "/tenant" + INIT_ADMIN_USER;
}
