package com.kizuna.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.member.api.dto.MemberHomeResponse;
import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberSelfServiceTest {

  @Mock private PlatformUserRepository platformUserRepository;
  @Mock private MemberRepository memberRepository;

  @InjectMocks private MemberSelfService service;

  private PlatformUser memberUser(long id) {
    PlatformUser user =
        PlatformUser.builder()
            .email("member@example.com")
            .password("encoded")
            .displayName("会員 花子")
            .enabled(true)
            .userType(UserType.MEMBER)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of())
            .build();
    user.setId(id);
    return user;
  }

  @Test
  @DisplayName("home は本人の会員コードと表示名を返す")
  void homeReturnsOwnMemberCodeAndDisplayName() {
    Mockito.when(platformUserRepository.findByEmail("member@example.com"))
        .thenReturn(Optional.of(memberUser(10L)));
    Mockito.when(memberRepository.findByPlatformUserId(10L))
        .thenReturn(
            Optional.of(Member.builder().memberCode("123456789012").platformUserId(10L).build()));

    MemberHomeResponse response = service.home("member@example.com");

    assertThat(response.memberCode()).isEqualTo("123456789012");
    assertThat(response.displayName()).isEqualTo("会員 花子");
  }

  @Test
  @DisplayName("認証主体のユーザーが存在しない場合は 401 系例外")
  void homeThrowsWhenUserMissing() {
    Mockito.when(platformUserRepository.findByEmail("member@example.com"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.home("member@example.com"))
        .isInstanceOf(StaleSessionException.class);
  }

  @Test
  @DisplayName("会員行が存在しない場合は 401 系例外（登録と会員作成は原子的で、欠損は主体の破損を意味する）")
  void homeThrowsWhenMemberRowMissing() {
    Mockito.when(platformUserRepository.findByEmail("member@example.com"))
        .thenReturn(Optional.of(memberUser(10L)));
    Mockito.when(memberRepository.findByPlatformUserId(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.home("member@example.com"))
        .isInstanceOf(StaleSessionException.class);
  }
}
