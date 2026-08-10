package com.kizuna.store.infrastructure;

import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.store.application.StoreActivationService;
import com.kizuna.user.domain.PermissionCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 店舗側の利用者が店舗コンソールへ着地したことを引き金に、準備中の店舗を稼働中へ移すインターセプタ。
 *
 * <p>{@link StoreExistenceInterceptor} の後段に同一 mount で登録する。ここまで来た要求は {@link
 * com.kizuna.shared.storescope.StoreIdInterceptor} の授権検査（storeBridge claim と授権店舗集合）と
 * 実在性検査を通っており、「店舗文脈を正当に確立できた 1 リクエスト」がちょうど遷移の引き金にあたる。 遷移の口をここ 1 箇所に閉じるため、平台側の {@link
 * com.kizuna.shared.storescope.StoreScopeExecutor} には引き金を置かない — あちらは HQ が店舗を跨いで操作する経路で、店舗の開店を意味しない。
 *
 * <p>引き金を引かない要求が 2 種類ある。ひとつは未認証で、公開サイトの閲覧はヘッダだけで店舗文脈を名乗れるため、 これを数えると誰も入っていない店舗が開店してしまう。もうひとつは HQ
 * 利用者で、運営側の下見は店舗の開店ではない。 HQ の判定は保持権限がプラットフォームコンソールに属するかで行い、ログイン時の着地先導出と同じ規則に揃える。
 *
 * <p>遷移は要求の目的ではなく副作用なので、書き込みに失敗しても要求そのものは通す。ただし版の競合の相手は 同時着地の活性化とは限らず、HQ
 * の店舗更新（名称・メール）でも版は進む。相手が稼働中を書いたとは限らないため、 競合したら一度だけ取り直して再試行し、それでも競合するなら次の着地の再試行に委ねる。
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class StoreActivationInterceptor implements HandlerInterceptor {

  private final StoreContext storeContext;
  private final StoreActivationService storeActivationService;

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {
    if (!storeContext.hasStoreId()) {
      return true;
    }
    if (!(SecurityContextHolder.getContext().getAuthentication()
        instanceof JwtAuthenticationToken authentication)) {
      return true;
    }
    if (holdsPlatformConsole(authentication)) {
      return true;
    }
    try {
      storeActivationService.activateOnConsoleAccess(storeContext.getStoreId());
    } catch (OptimisticLockingFailureException e) {
      try {
        storeActivationService.activateOnConsoleAccess(storeContext.getStoreId());
      } catch (OptimisticLockingFailureException retryFailure) {
        log.debug("店舗の稼働中への遷移が再競合したため次の着地に委ねる storeId: {}", storeContext.getStoreId());
      }
    }
    return true;
  }

  /** 保持権限がひとつでもプラットフォームコンソールに属するか（＝HQ 利用者か）。 */
  private static boolean holdsPlatformConsole(Authentication authentication) {
    Set<String> held = authorityNames(authentication.getAuthorities());
    return Arrays.stream(PermissionCode.values())
        .filter(permission -> permission.getConsole() == PermissionCode.Console.PLATFORM)
        .map(PermissionCode::authority)
        .anyMatch(held::contains);
  }

  private static Set<String> authorityNames(Collection<? extends GrantedAuthority> authorities) {
    return authorities.stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toUnmodifiableSet());
  }
}
