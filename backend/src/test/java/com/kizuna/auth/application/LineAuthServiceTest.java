package com.kizuna.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.auth.api.dto.LineAuthorizationRequest;
import com.kizuna.auth.api.dto.LineLoginResponse;
import com.kizuna.auth.api.dto.LineRegistrationRequest;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.LineApiClient;
import com.kizuna.auth.infrastructure.LineChannel;
import com.kizuna.auth.infrastructure.LineChannelResolver;
import com.kizuna.auth.infrastructure.LineIdentity;
import com.kizuna.auth.infrastructure.LineRegistrationTicketStore;
import com.kizuna.member.application.MemberRegistrationService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.user.domain.LineAlreadyLinkedException;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.DisabledException;

/** {@link LineAuthService} の単体テスト。 */
@ExtendWith(MockitoExtension.class)
class LineAuthServiceTest {

  private static final LineChannel CHANNEL = new LineChannel("channel-id", "channel-secret");
  private static final LineIdentity IDENTITY = new LineIdentity("U-line-1", "LINE太郎");
  private static final Token TOKEN = new Token("issued-token", 1_700_000_000_000L);

  @Mock private LineChannelResolver channelResolver;
  @Mock private LineApiClient lineApiClient;
  @Mock private LineRegistrationTicketStore ticketStore;
  @Mock private PlatformUserRepository userRepository;
  @Mock private MemberRegistrationService memberRegistrationService;
  @Mock private PlatformAuthService authService;

  @InjectMocks private LineAuthService lineAuthService;

  private static LineAuthorizationRequest authorizationRequest() {
    LineAuthorizationRequest request = new LineAuthorizationRequest();
    request.setCode("auth-code");
    request.setRedirectUri("https://app.test/cb");
    request.setCodeVerifier("verifier");
    return request;
  }

  private static LineRegistrationRequest registrationRequest() {
    LineRegistrationRequest request = new LineRegistrationRequest();
    request.setRegistrationTicket("ticket-1");
    request.setDisplayName("会員太郎");
    request.setEmail("member@kizuna.test");
    return request;
  }

  private static PlatformUser member(String email) {
    return PlatformUser.builder()
        .email(email)
        .password("hash")
        .displayName("会員太郎")
        .enabled(true)
        .userType(UserType.MEMBER)
        .storeScopeType(StoreScopeType.SPECIFIC_STORES)
        .storeIds(Set.of())
        .build();
  }

  private void stubVerifiedIdentity() {
    when(channelResolver.resolve()).thenReturn(Optional.of(CHANNEL));
    when(lineApiClient.exchangeAndVerify(CHANNEL, "auth-code", "https://app.test/cb", "verifier"))
        .thenReturn(IDENTITY);
  }

  @Test
  @DisplayName("チャネル解決できれば config は enabled=true とチャネル ID を返す")
  void configExposesChannelIdWhenEnabled() {
    when(channelResolver.resolve()).thenReturn(Optional.of(CHANNEL));

    assertThat(lineAuthService.config().enabled()).isTrue();
    assertThat(lineAuthService.config().channelId()).isEqualTo("channel-id");
  }

  @Test
  @DisplayName("チャネル未設定なら config は enabled=false でチャネル ID を返さない")
  void configHidesChannelIdWhenDisabled() {
    when(channelResolver.resolve()).thenReturn(Optional.empty());

    assertThat(lineAuthService.config().enabled()).isFalse();
    assertThat(lineAuthService.config().channelId()).isNull();
  }

  @Test
  @DisplayName("連携済み LINE ユーザーはパスワードログインと同一の発行経路でトークンを得る")
  void loginWithLinkedLineUserIssuesToken() {
    stubVerifiedIdentity();
    PlatformUser user = member("member@kizuna.test");
    when(userRepository.findByLineUserId("U-line-1")).thenReturn(Optional.of(user));
    when(authService.issueTokenFor(user)).thenReturn(TOKEN);

    LineLoginResponse response = lineAuthService.login(authorizationRequest());

    assertThat(response.registered()).isTrue();
    assertThat(response.token()).isEqualTo("issued-token");
    assertThat(response.expiresAt()).isEqualTo(1_700_000_000_000L);
    assertThat(response.registrationTicket()).isNull();
  }

  @Test
  @DisplayName("未知の LINE ユーザーには登録チケットを返し、トークンは一切発行しない")
  void loginWithUnknownLineUserReturnsRegistrationTicket() {
    stubVerifiedIdentity();
    when(userRepository.findByLineUserId("U-line-1")).thenReturn(Optional.empty());
    when(ticketStore.issue("U-line-1")).thenReturn("ticket-1");

    LineLoginResponse response = lineAuthService.login(authorizationRequest());

    assertThat(response.registered()).isFalse();
    assertThat(response.registrationTicket()).isEqualTo("ticket-1");
    assertThat(response.displayName()).isEqualTo("LINE太郎");
    assertThat(response.token()).isNull();
    verify(authService, never()).issueTokenFor(any());
  }

  @Test
  @DisplayName("ログイン経路はメールアドレスで身分を引かない（LINE 側メール一致による既存アカウントのなりすましを構造的に排除する）")
  void loginNeverResolvesIdentityByEmail() {
    stubVerifiedIdentity();
    when(userRepository.findByLineUserId("U-line-1")).thenReturn(Optional.empty());
    when(ticketStore.issue("U-line-1")).thenReturn("ticket-1");

    lineAuthService.login(authorizationRequest());

    verify(userRepository, never()).findByEmail(anyString());
    verify(userRepository, never()).findByEmailForUpdate(anyString());
  }

  @Test
  @DisplayName("停止済みアカウントの LINE ログインはパスワードログインと同じく 401 相当で拒否する")
  void loginWithDisabledLinkedUserIsRejected() {
    stubVerifiedIdentity();
    PlatformUser user = member("member@kizuna.test");
    user.stop();
    when(userRepository.findByLineUserId("U-line-1")).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> lineAuthService.login(authorizationRequest()))
        .isInstanceOf(DisabledException.class);
    verify(authService, never()).issueTokenFor(any());
  }

  @Test
  @DisplayName("チャネル未設定なら LINE ログインは 503 相当で拒否する")
  void loginWithoutChannelIsUnavailable() {
    when(channelResolver.resolve()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> lineAuthService.login(authorizationRequest()))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  @DisplayName("登録はチケット裏の LINE ユーザー ID で会員を作り、成功時のみチケットを消費してトークンを発行する")
  void registerConsumesTicketAndIssuesToken() {
    when(ticketStore.peek("ticket-1")).thenReturn(Optional.of("U-line-1"));
    PlatformUser created = member("member@kizuna.test");
    when(memberRegistrationService.registerWithLine("member@kizuna.test", "会員太郎", "U-line-1"))
        .thenReturn(created);
    when(authService.issueTokenFor(created)).thenReturn(TOKEN);

    assertThat(lineAuthService.register(registrationRequest())).isEqualTo(TOKEN);
    verify(ticketStore).consume("ticket-1");
  }

  @Test
  @DisplayName("無効・使用済みチケットでの登録は 400 相当で拒否し、会員を作らない")
  void registerWithInvalidTicketIsRejected() {
    when(ticketStore.peek("ticket-1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> lineAuthService.register(registrationRequest()))
        .isInstanceOf(ServiceException.class);
    verify(memberRegistrationService, never())
        .registerWithLine(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("登録が重複などで失敗してもチケットは消費せず、入力を直しての再試行を許す")
  void registerKeepsTicketWhenRegistrationFails() {
    when(ticketStore.peek("ticket-1")).thenReturn(Optional.of("U-line-1"));
    when(memberRegistrationService.registerWithLine("member@kizuna.test", "会員太郎", "U-line-1"))
        .thenThrow(new ConflictException("このメールアドレスは既に登録されています。ログインしてご利用ください"));

    assertThatThrownBy(() -> lineAuthService.register(registrationRequest()))
        .isInstanceOf(ConflictException.class);
    verify(ticketStore, never()).consume(anyString());
  }

  @Test
  @DisplayName("連携は現在の認証主体に結び付ける")
  void linkAttachesLineUserToAuthenticatedUser() {
    stubVerifiedIdentity();
    PlatformUser user = member("member@kizuna.test");
    when(userRepository.findByEmail("member@kizuna.test")).thenReturn(Optional.of(user));
    when(userRepository.existsByLineUserId("U-line-1")).thenReturn(false);

    lineAuthService.link("member@kizuna.test", authorizationRequest());

    assertThat(user.getLineUserId()).isEqualTo("U-line-1");
    verify(userRepository).saveAndFlush(user);
  }

  @Test
  @DisplayName("事前チェックを擦り抜けた並行連携は一意制約違反を 409 に写像する")
  void linkMapsConcurrentUniqueViolationToConflict() {
    stubVerifiedIdentity();
    PlatformUser user = member("member@kizuna.test");
    when(userRepository.findByEmail("member@kizuna.test")).thenReturn(Optional.of(user));
    when(userRepository.existsByLineUserId("U-line-1")).thenReturn(false);
    when(userRepository.saveAndFlush(user))
        .thenThrow(
            new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException(
                    "ERROR: duplicate key value violates unique constraint"
                        + " \"uq_t_users_line_user_id\"")));

    assertThatThrownBy(() -> lineAuthService.link("member@kizuna.test", authorizationRequest()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("連携済み本人の再連携は同じ LINE アカウントでも本人側の不変条件で拒否する（他人に取られた旨の誤った理由を返さない）")
  void relinkingAlreadyLinkedAccountReportsOwnLinkage() {
    stubVerifiedIdentity();
    PlatformUser user = member("member@kizuna.test");
    user.linkLine("U-line-1");
    when(userRepository.findByEmail("member@kizuna.test")).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> lineAuthService.link("member@kizuna.test", authorizationRequest()))
        .isInstanceOf(LineAlreadyLinkedException.class);
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("他の身分が連携済みの LINE アカウントは 409 で拒否し、保存しない")
  void linkRejectsLineUserAlreadyBoundToAnotherAccount() {
    stubVerifiedIdentity();
    when(userRepository.existsByLineUserId("U-line-1")).thenReturn(true);
    when(userRepository.findByEmail("member@kizuna.test"))
        .thenReturn(Optional.of(member("member@kizuna.test")));

    assertThatThrownBy(() -> lineAuthService.link("member@kizuna.test", authorizationRequest()))
        .isInstanceOf(ConflictException.class);
    verify(userRepository, never()).save(any());
  }
}
