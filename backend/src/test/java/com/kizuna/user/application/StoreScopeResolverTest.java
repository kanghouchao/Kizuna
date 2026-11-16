package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.web.JwtClaims;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class StoreScopeResolverTest {

  private static final long USER_ID = 42L;
  private static final String EMAIL = "actor@kizuna.test";

  private final UserRepository users = mock(UserRepository.class);
  private final StoreScopeResolver resolver = new StoreScopeResolver(users);

  private static Jwt token() {
    return tokenWithoutUserId().claim(JwtClaims.USER_ID, USER_ID).build();
  }

  private static Jwt.Builder tokenWithoutUserId() {
    return Jwt.withTokenValue("token").header("alg", "none").subject(EMAIL);
  }

  private static User user(UserType type, Set<Long> storeIds) {
    return User.builder()
        .email(EMAIL)
        .password("hash")
        .displayName("担当")
        .enabled(true)
        .userType(type)
        .roleIds(type.holdsRoles() ? Set.of(1L) : Set.of())
        .storeIds(storeIds)
        .build();
  }

  @Test
  @DisplayName("利用者 ID の claim が無ければ null。subject の電子メールに店舗を持つ行があっても引かない")
  void missingUserIdClaimResolvesToNull() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserType.STAFF, Set.of(1L))));

    assertThat(resolver.resolve(tokenWithoutUserId().build())).isNull();
    verify(users, never()).findById(any());
  }

  @Test
  @DisplayName("利用者 ID の claim が数値でなければ null")
  void nonNumericUserIdClaimResolvesToNull() {
    var jwt = tokenWithoutUserId().claim(JwtClaims.USER_ID, String.valueOf(USER_ID)).build();

    assertThat(resolver.resolve(jwt)).isNull();
    verify(users, never()).findById(any());
  }

  @Test
  @DisplayName("利用者 ID の行が無ければ null。subject の電子メールに店舗を持つ行があっても引かない")
  void missingRowResolvesToNull() {
    when(users.findById(USER_ID)).thenReturn(Optional.empty());
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserType.STAFF, Set.of(1L))));

    assertThat(resolver.resolve(token())).isNull();
    verify(users, never()).findByEmail(any());
  }

  @Test
  @DisplayName("退会済みの STAFF は行に店舗集合が残っていても作用域を持たない")
  void withdrawnStaffResolvesToNull() {
    User withdrawn = user(UserType.STAFF, Set.of(1L, 2L));
    withdrawn.markWithdrawn(OffsetDateTime.parse("2026-01-01T00:00:00+09:00"));
    when(users.findById(USER_ID)).thenReturn(Optional.of(withdrawn));

    assertThat(resolver.resolve(token())).isNull();
  }

  @Test
  @DisplayName("STAFF は本人の行の店舗集合をそのまま作用域にする")
  void staffResolvesFromTheRow() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user(UserType.STAFF, Set.of(1L, 2L))));

    var scope = resolver.resolve(token());

    assertThat(scope.storeIds()).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  @DisplayName("利用者 ID の claim は整数型の幅に依らず同じ行を引く")
  void integerUserIdClaimResolvesTheSameRow() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user(UserType.STAFF, Set.of(1L))));
    var jwt = tokenWithoutUserId().claim(JwtClaims.USER_ID, (int) USER_ID).build();

    assertThat(resolver.resolve(jwt).storeIds()).containsExactly(1L);
  }

  @Test
  @DisplayName("店舗集合が空の STAFF は空の作用域になり、どの店舗も授権しない")
  void emptySetResolvesToEmptyScope() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user(UserType.STAFF, Set.of())));

    var scope = resolver.resolve(token());

    assertThat(scope.storeIds()).isEmpty();
    assertThat(scope.authorizes(1L)).isFalse();
  }

  @Test
  @DisplayName("CAST は招待で店舗を持っていても作用域を持たない（店舗文脈を名乗れるのはロールで授権される種別だけ）")
  void castWithStoresResolvesToNull() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user(UserType.CAST, Set.of(1L))));

    assertThat(resolver.resolve(token())).isNull();
  }

  @Test
  @DisplayName("MEMBER も作用域を持たない")
  void memberResolvesToNull() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user(UserType.MEMBER, Set.of(1L))));

    assertThat(resolver.resolve(token())).isNull();
  }
}
