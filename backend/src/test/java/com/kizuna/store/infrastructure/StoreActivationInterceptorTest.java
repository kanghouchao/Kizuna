package com.kizuna.store.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.store.application.StoreActivationService;
import com.kizuna.user.domain.PermissionCode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class StoreActivationInterceptorTest {

  private static final long STORE_ID = 7L;

  private StoreContext storeContext;
  private StoreActivationService storeActivationService;
  private StoreActivationInterceptor interceptor;

  @BeforeEach
  void setUp() {
    storeContext = new StoreContext();
    storeActivationService = mock(StoreActivationService.class);
    interceptor = new StoreActivationInterceptor(storeContext, storeActivationService);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authenticateWith(String... authorities) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("staff@kizuna.test")
            .issuer("PlatformAuth")
            .build();
    List<SimpleGrantedAuthority> granted =
        Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, granted));
  }

  private boolean preHandle() {
    return interceptor.preHandle(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
  }

  @Test
  @DisplayName("店舗側利用者の店舗文脈リクエストが店舗を稼働中へ移すこと")
  void preHandle_storeUser_activatesStore() {
    storeContext.setStoreId(STORE_ID);
    authenticateWith(PermissionCode.ORDER_MANAGE.authority());

    preHandle();

    verify(storeActivationService).activateOnConsoleAccess(STORE_ID);
  }

  // 運営側の下見は開店ではない。店舗権限も併せ持つ混成束の HQ 利用者もここで除かれる。
  @Test
  @DisplayName("プラットフォーム権限の保持者は店舗文脈で入っても店舗を稼働中へ移さないこと")
  void preHandle_platformUser_doesNotActivate() {
    storeContext.setStoreId(STORE_ID);
    authenticateWith(
        PermissionCode.ORDER_MANAGE.authority(), PermissionCode.STORE_MANAGE.authority());

    preHandle();

    verify(storeActivationService, never()).activateOnConsoleAccess(STORE_ID);
  }

  // 公開サイトはヘッダだけで店舗文脈を名乗れるため、閲覧を数えると誰も入っていない店舗が開店してしまう。
  @Test
  @DisplayName("未認証の店舗文脈リクエストは店舗を稼働中へ移さないこと")
  void preHandle_unauthenticated_doesNotActivate() {
    storeContext.setStoreId(STORE_ID);

    preHandle();

    verify(storeActivationService, never()).activateOnConsoleAccess(STORE_ID);
  }

  @Test
  @DisplayName("店舗文脈が無いリクエストは店舗を稼働中へ移さないこと")
  void preHandle_withoutStoreContext_doesNotActivate() {
    authenticateWith(PermissionCode.ORDER_MANAGE.authority());

    preHandle();

    verify(storeActivationService, never()).activateOnConsoleAccess(STORE_ID);
  }

  // 遷移は要求の目的ではない副作用なので、競合で書き負けても要求そのものは通す。
  @Test
  @DisplayName("遷移が競合しても要求そのものは通ること")
  void preHandle_whenActivationConflicts_stillProceeds() {
    storeContext.setStoreId(STORE_ID);
    authenticateWith(PermissionCode.ORDER_MANAGE.authority());
    doThrow(new OptimisticLockingFailureException("競合"))
        .when(storeActivationService)
        .activateOnConsoleAccess(STORE_ID);

    assertThat(preHandle()).isTrue();
  }

  // SHARED 権限は HQ と店舗の双方が持つため、これで HQ を判定すると店舗側利用者まで除かれる。
  @Test
  @DisplayName("跨店参照系（SHARED）だけの保持者は HQ とみなされないこと")
  void preHandle_sharedOnlyAuthority_isNotTreatedAsPlatformUser() {
    storeContext.setStoreId(STORE_ID);
    authenticateWith(PermissionCode.STORE_VIEW.authority());

    preHandle();

    verify(storeActivationService).activateOnConsoleAccess(STORE_ID);
  }
}
