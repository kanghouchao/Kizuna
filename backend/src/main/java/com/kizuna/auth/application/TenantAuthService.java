package com.kizuna.auth.application;

import com.kizuna.auth.api.dto.TenantRegisterRequest;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.tenant.domain.Tenant;

/**
 * テナント認証サービスのインターフェース。
 *
 * <p>このインターフェースは、テナントユーザーの認証とテナントユーザーの初期化に関する メソッドを定義しています。
 */
public interface TenantAuthService {

  /**
   * ログイン処理を行い、成功した場合はJWTトークンを返す。
   *
   * @param username the username
   * @param password the password
   * @return the JWT token
   */
  Token login(String username, String password);

  /**
   * テナントユーザーの初期化を行う。
   *
   * @param tenant リクエストからのテナント登録情報
   * @return 初期化されたテナントエンティティ
   */
  Tenant initializeAdminUser(TenantRegisterRequest tenant);

  /**
   * パスワードを変更する。現在のパスワードが一致しない場合は {@link com.kizuna.shared.exception.ServiceException}。
   *
   * @param email 対象ユーザーのメールアドレス
   * @param currentPassword 現在のパスワード（平文）
   * @param newPassword 新しいパスワード（平文）
   */
  void changePassword(String email, String currentPassword, String newPassword);
}
