package com.kizuna.shared.storescope;

import com.kizuna.shared.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 拒否は例外送出で表明し、応答の成形は {@link com.kizuna.shared.exception.CommonExceptionHandler} へ委ねる — ここでは
 * response へ直接書かない。同じ status を返す経路が複数のワイヤ形を持つと、呼出側が応答体の形を一つに決められなくなるため。
 */
@Log4j2
@Component
@AllArgsConstructor
public class StoreIdInterceptor implements HandlerInterceptor {

  private final StoreContext storeContext;

  private static final String HEADER_ROLE = "X-Role";
  private static final String HEADER_STORE_ID = "X-Store-ID";
  private static final String HEADER_ROLE_STORE = "store";

  /**
   * 平台トークンで店舗文脈（X-Store-ID）を確立できるかを示す claim 名。値はログイン時に STORE
   * コンソール能力の保持から導出される（PlatformAuthService）。 shared 層は user モジュールへ依存しないため能力目録を参照せず、導出済みの boolean
   * claim だけを消費する。 {@link StoreBridgeGuard} と共有するためパッケージ可視とする。
   */
  static final String CLAIM_STORE_BRIDGE = "storeBridge";

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {
    Jwt claims = authenticatedClaims();
    StoreScope scope =
        StoreScope.fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    boolean claimsStoreHeaderPresent =
        HEADER_ROLE_STORE.equals(request.getHeader(HEADER_ROLE))
            && StringUtils.isNotBlank(request.getHeader(HEADER_STORE_ID));
    if (scope != null) {
      // 平台トークンの橋渡し: X-Role: store + 数値 X-Store-ID を要求し、授権店舗集合で検証する（fail-closed）。
      if (HEADER_ROLE_STORE.equals(request.getHeader(HEADER_ROLE))
          && StringUtils.isNumeric(request.getHeader(HEADER_STORE_ID))) {
        // 店舗文脈を名乗れるのは STORE コンソール能力の保持者（storeBridge claim）のみ。CAST/MEMBER/HQ が
        // 店舗ヘッダを名乗るのは詐称として 403 で拒否する。授権スコープ検証より前に弾き、
        // isAuthenticated() のみの端点（/files/upload 等）への店舗文脈確立の漏れを塞ぐ。
        if (!Boolean.TRUE.equals(claims.getClaimAsBoolean(CLAIM_STORE_BRIDGE))) {
          throw new AccessDeniedException("店舗コンソールの権限がありません");
        }
        Long headerValue = tryParseStoreId(request.getHeader(HEADER_STORE_ID));
        if (headerValue == null) {
          // 全桁数字だが long 範囲外。
          throw new ServiceException("店舗 ID の形式が正しくありません");
        }
        if (!scope.authorizes(headerValue)) {
          throw new AccessDeniedException("この店舗の権限がありません");
        }
        this.storeContext.setStoreId(headerValue);
        return true;
      }
      // ヘッダ不備（X-Role 欠落・非数値含む）は店舗文脈なし → 末尾の @StoreOptional 判定へ落とす。
    } else if (claims != null) {
      // 認証済みだが storeId claim を持たない（プラットフォーム PlatformAuth 発行、または storeId 無しの legacy）。
      // ヘッダで店舗を名乗る主張は詐称として 403 で拒否する。詐称ヘッダが無ければ下の
      // @StoreOptional 判定に委ね、プラットフォームの正当な素通り（例: /files/upload の platform 保存）を保つ。
      if (claimsStoreHeaderPresent) {
        throw new AccessDeniedException("店舗文脈を名乗る権限がありません");
      }
    } else if (claimsStoreHeaderPresent
        && StringUtils.isNumeric(request.getHeader(HEADER_STORE_ID))) {
      // 未認証（ログイン前・公開ページ）だけがヘッダのみで店舗を名乗れる。
      Long headerValue = tryParseStoreId(request.getHeader(HEADER_STORE_ID));
      if (headerValue == null) {
        // 全桁数字だが long 範囲外。
        throw new ServiceException("店舗 ID の形式が正しくありません");
      }
      this.storeContext.setStoreId(headerValue);
      return true;
    }
    // JWT claim・ヘッダのいずれからも店舗文脈を解決できなかった。文脈が無いまま素通りさせると
    // storeFilter が有効化されず @StoreScoped クエリが全店舗の行を返す（fail-open）ため、
    // @StoreOptional を明示したエンドポイントに限り許可し、それ以外は 403 で拒否する（fail-closed）。
    if (handler instanceof HandlerMethod handlerMethod
        && handlerMethod.hasMethodAnnotation(StoreOptional.class)) {
      return true;
    }
    throw new AccessDeniedException("店舗文脈が解決できません");
  }

  /**
   * 認証済みリクエストの JWT claim 集合（resource-server が構築したもの）を返す。 未認証、または非
   * JwtAuthenticationToken（匿名認証やログイン前など）の場合は null。
   */
  private Jwt authenticatedClaims() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return AuthenticatedJwt.from(authentication);
  }

  /** 全桁数字の文字列を long に変換する。long 範囲を超える場合は null（呼び出し側が 400 を返す）。 */
  private static Long tryParseStoreId(String numericValue) {
    try {
      return Long.parseLong(numericValue);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public void afterCompletion(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler,
      @Nullable Exception ex) {
    storeContext.clear();
  }
}
