package com.kizuna.service.tenant.auth;

import com.kizuna.model.dto.auth.Token;
import com.kizuna.model.dto.tenant.TenantRegisterRequest;
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
}
