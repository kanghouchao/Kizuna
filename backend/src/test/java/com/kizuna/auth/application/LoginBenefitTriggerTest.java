package com.kizuna.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kizuna.point.application.BenefitGrantService;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserType;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LoginBenefitTriggerTest {

  @Mock private BenefitGrantService benefitGrantService;

  @InjectMocks private LoginBenefitTrigger trigger;

  @Test
  @DisplayName("会員の認証成功はログイン特典の生産者へ利用者 ID で渡ること")
  void aMemberLoginReachesTheProducer() {
    trigger.onAuthenticated(user(UserType.MEMBER, 70L));

    verify(benefitGrantService).grantLoginBenefits(70L);
  }

  @Test
  @DisplayName("会員以外の認証成功は生産者へ渡らないこと")
  void nonMemberLoginsNeverReachTheProducer() {
    trigger.onAuthenticated(user(UserType.STAFF, 1L));
    trigger.onAuthenticated(user(UserType.CAST, 2L));

    verifyNoInteractions(benefitGrantService);
  }

  @Test
  @DisplayName("付与側の例外は記録して吞み、認証の応答を変えないこと")
  void producerFailuresAreSwallowed() {
    doThrow(new IllegalStateException("台帳に書けない"))
        .when(benefitGrantService)
        .grantLoginBenefits(anyLong());

    assertThatCode(() -> trigger.onAuthenticated(user(UserType.MEMBER, 70L)))
        .doesNotThrowAnyException();
  }

  private static User user(UserType type, long id) {
    User user =
        User.builder()
            .email("someone@kizuna.test")
            .password("hash")
            .displayName("誰か")
            .enabled(true)
            .userType(type)
            .roleIds(type == UserType.STAFF ? Set.of(10L) : Set.of())
            .storeScopeType(
                type == UserType.STAFF ? StoreScopeType.ALL_STORES : StoreScopeType.SPECIFIC_STORES)
            .storeIds(type == UserType.CAST ? Set.of(1L) : Set.of())
            .build();
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
