package com.kizuna.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.member.api.dto.MemberRegistrationRequest;
import com.kizuna.member.api.dto.MemberRegistrationResponse;
import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberRegistrationServiceTest {

  @Mock private PlatformUserRepository platformUserRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private MemberRegistrationService service;

  private MemberRegistrationRequest request() {
    MemberRegistrationRequest request = new MemberRegistrationRequest();
    request.setEmail("member@example.com");
    request.setPassword("password123");
    request.setDisplayName("会員 花子");
    return request;
  }

  private PlatformUser savedUser(long id) {
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
  @DisplayName("登録は MEMBER 身分（ロールなし・紐づけ店舗なし）と数字 12 桁の会員コード付き会員を作成する")
  void registerCreatesMemberIdentityAndMemberWithCode() {
    when(platformUserRepository.findByEmail("member@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("password123")).thenReturn("encoded");
    ArgumentCaptor<PlatformUser> userCaptor = ArgumentCaptor.forClass(PlatformUser.class);
    when(platformUserRepository.saveAndFlush(userCaptor.capture())).thenReturn(savedUser(10L));
    when(memberRepository.existsByMemberCode(anyString())).thenReturn(false);
    ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
    when(memberRepository.save(memberCaptor.capture()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MemberRegistrationResponse response = service.register(request());

    PlatformUser user = userCaptor.getValue();
    assertThat(user.getUserType()).isEqualTo(UserType.MEMBER);
    assertThat(user.getRoleIds()).isEmpty();
    assertThat(user.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(user.getStoreIds()).isEmpty();
    assertThat(user.getPassword()).isEqualTo("encoded");
    Member member = memberCaptor.getValue();
    assertThat(member.getPlatformUserId()).isEqualTo(10L);
    assertThat(member.getMemberCode()).matches("\\d{12}");
    assertThat(response.memberCode()).isEqualTo(member.getMemberCode());
  }

  @Test
  @DisplayName("既存 email の登録は 400 系例外で拒否し、身分・会員とも作成しない")
  void registerRejectsDuplicateEmail() {
    when(platformUserRepository.findByEmail("member@example.com"))
        .thenReturn(Optional.of(savedUser(10L)));

    assertThatThrownBy(() -> service.register(request())).isInstanceOf(ServiceException.class);

    verify(platformUserRepository, never()).saveAndFlush(any());
    verify(memberRepository, never()).save(any());
  }

  @Test
  @DisplayName("並行登録の一意制約違反（事前チェック通過後）は重複登録の 400 系例外に写像する")
  void registerMapsEmailConstraintViolationToServiceException() {
    when(platformUserRepository.findByEmail("member@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("password123")).thenReturn("encoded");
    when(platformUserRepository.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_t_users_email\""));

    assertThatThrownBy(() -> service.register(request())).isInstanceOf(ServiceException.class);

    verify(memberRepository, never()).save(any());
  }

  @Test
  @DisplayName("会員コードが衝突した場合は再生成して未使用のコードを採用する")
  void registerRetriesCodeGenerationOnCollision() {
    when(platformUserRepository.findByEmail("member@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("password123")).thenReturn("encoded");
    when(platformUserRepository.saveAndFlush(any())).thenReturn(savedUser(10L));
    when(memberRepository.existsByMemberCode(anyString())).thenReturn(true, false);
    when(memberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    MemberRegistrationResponse response = service.register(request());

    assertThat(response.memberCode()).matches("\\d{12}");
    verify(memberRepository, times(2)).existsByMemberCode(anyString());
  }
}
