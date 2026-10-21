package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class StoreScopeResolverTest {

  private final UserRepository users = mock(UserRepository.class);
  private final StoreScopeResolver resolver = new StoreScopeResolver(users);

  private static Jwt token() {
    return Jwt.withTokenValue("token").header("alg", "none").subject("actor@kizuna.test").build();
  }

  private static User user(UserType type, StoreScopeType scopeType, Set<Long> storeIds) {
    return User.builder()
        .email("actor@kizuna.test")
        .password("hash")
        .displayName("担当")
        .enabled(true)
        .userType(type)
        .roleIds(type.holdsRoles() ? Set.of(1L) : Set.of())
        .storeScopeType(scopeType)
        .storeIds(storeIds)
        .build();
  }

  @Test
  @DisplayName("主体の行が無ければ null（fail-closed の入力）")
  void missingSubjectResolvesToNull() {
    when(users.findByEmail("actor@kizuna.test")).thenReturn(Optional.empty());

    assertThat(resolver.resolve(token())).isNull();
  }

  @Test
  @DisplayName("STAFF は本人の行の店舗集合をそのまま作用域にする")
  void staffResolvesFromTheRow() {
    when(users.findByEmail("actor@kizuna.test"))
        .thenReturn(
            Optional.of(user(UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(1L, 2L))));

    var scope = resolver.resolve(token());

    assertThat(scope.allStores()).isFalse();
    assertThat(scope.storeIds()).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  @DisplayName("ALL_STORES は全店可視")
  void allStoresResolvesToAllStores() {
    when(users.findByEmail("actor@kizuna.test"))
        .thenReturn(Optional.of(user(UserType.STAFF, StoreScopeType.ALL_STORES, Set.of())));

    assertThat(resolver.resolve(token()).allStores()).isTrue();
  }

  @Test
  @DisplayName("CAST は招待で店舗を持っていても作用域を持たない（店舗文脈を名乗れるのはロールで授権される種別だけ）")
  void castWithStoresResolvesToNull() {
    when(users.findByEmail("actor@kizuna.test"))
        .thenReturn(Optional.of(user(UserType.CAST, StoreScopeType.SPECIFIC_STORES, Set.of(1L))));

    assertThat(resolver.resolve(token())).isNull();
  }

  @Test
  @DisplayName("MEMBER も作用域を持たない")
  void memberResolvesToNull() {
    when(users.findByEmail("actor@kizuna.test"))
        .thenReturn(Optional.of(user(UserType.MEMBER, StoreScopeType.SPECIFIC_STORES, Set.of())));

    assertThat(resolver.resolve(token())).isNull();
  }
}
