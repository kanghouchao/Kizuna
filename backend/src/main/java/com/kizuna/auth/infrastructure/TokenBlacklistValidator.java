package com.kizuna.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * トークン単位・ユーザー単位の Redis ブラックリスト（{@link TokenBlacklistService}）を検証する {@code
 * OAuth2TokenValidator}。判定は毎回 Redis へ問い合わせる必要があるため、{@link MultiHmacJwtDecoderFactory} が組み立てる
 * decoder にはキャッシュを付けないこと（付けるとブラックリストの即時性が壊れる）。
 *
 * <p>失敗時の {@link OAuth2Error} には内部理由（トークン失効かユーザー停止か等）を含めない。 Redis
 * 接続断で例外が投げられた場合はこの検証を貫通して呼び出し元へ伝播し、リクエストは 500 になる（現行 filter と同じ fail-closed）。
 */
@Component
@RequiredArgsConstructor
public class TokenBlacklistValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token");

  private final TokenBlacklistService tokenBlacklistService;

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    if (tokenBlacklistService.isBlacklisted(jwt.getTokenValue())
        || tokenBlacklistService.isUserBlacklisted(jwt.getSubject())) {
      return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }
    return OAuth2TokenValidatorResult.success();
  }
}
