package com.kizuna.shared.web;

import com.kizuna.shared.exception.StaleSessionException;
import java.security.Principal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * 発行と検証が合意する JWT の claim 名。利用者 ID は subject（電子メール）と違って退会で解放されず、後継の身分へ移らない （ADR
 * 0029）ため、本人を名指す操作はこちらで対象を解決する。
 */
public final class JwtClaims {

  /** 資格情報の版の鍵と、本人操作の対象を固定する利用者 ID。 */
  public static final String USER_ID = "userId";

  private JwtClaims() {}

  /**
   * 検証済み Bearer トークンの利用者 ID。handler は {@link Principal} で受ける（{@code @AuthenticationPrincipal} の解決器は
   * MVC 単体の文脈に無い）。検証段階で欠落は拒まれているが、Bearer 以外の主体や型の取り違えは主体不在として扱う。
   */
  public static long userIdOf(Principal principal) {
    if (principal instanceof JwtAuthenticationToken token
        && token.getToken().getClaims().get(USER_ID) instanceof Number id) {
      return id.longValue();
    }
    throw new StaleSessionException("認証セッションの主体が存在しません");
  }
}
