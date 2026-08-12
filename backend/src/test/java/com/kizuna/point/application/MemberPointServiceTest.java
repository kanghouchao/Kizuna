package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.point.api.dto.MemberPointEntryResponse;
import com.kizuna.point.domain.MemberPointEntryView;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.point.domain.PointEntryType;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class MemberPointServiceTest {

  private static final String EMAIL = "member@example.com";
  private static final long PLATFORM_USER_ID = 10L;
  private static final long MEMBER_ID = 7L;

  @Mock private PlatformUserRepository platformUserRepository;
  @Mock private MemberLookupService memberLookupService;
  @Mock private PointEntryRepository pointEntryRepository;
  @Mock private PointLedgerService pointLedgerService;
  @Mock private AppProperties appProperties;

  @InjectMocks private MemberPointService service;

  @Captor private ArgumentCaptor<Limit> limitCaptor;

  /** 明細 1 行の読み側 projection の代役。 */
  private record View(
      Long id,
      OffsetDateTime createdAt,
      PointEntryType entryType,
      Integer amount,
      LocalDate expiresOn,
      String storeName)
      implements MemberPointEntryView {

    @Override
    public Long getId() {
      return id;
    }

    @Override
    public OffsetDateTime getCreatedAt() {
      return createdAt;
    }

    @Override
    public PointEntryType getEntryType() {
      return entryType;
    }

    @Override
    public Integer getAmount() {
      return amount;
    }

    @Override
    public LocalDate getExpiresOn() {
      return expiresOn;
    }

    @Override
    public String getStoreName() {
      return storeName;
    }
  }

  private static View grant(long id, String createdAt) {
    return new View(
        id, OffsetDateTime.parse(createdAt), PointEntryType.ORDER_GRANT, 100, null, "店舗A");
  }

  @BeforeEach
  void resolveAuthenticatedMember() {
    PlatformUser user =
        PlatformUser.builder()
            .email(EMAIL)
            .password("encoded")
            .displayName("会員 花子")
            .enabled(true)
            .userType(UserType.MEMBER)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of())
            .build();
    user.setId(PLATFORM_USER_ID);
    Mockito.lenient().when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    Mockito.lenient()
        .when(memberLookupService.findByPlatformUserId(PLATFORM_USER_ID))
        .thenReturn(Optional.of(new MemberLookup(MEMBER_ID, "123456789012")));
  }

  @Test
  @DisplayName("残高は本人の台帳の合計を返す")
  void balanceReturnsOwnLedgerTotal() {
    Mockito.when(pointLedgerService.balance(MEMBER_ID)).thenReturn(1200L);

    assertThat(service.balance(EMAIL).balance()).isEqualTo(1200L);
  }

  @Test
  @DisplayName("明細は種別を問わず本人の仕訳を返し、増減の符号をそのまま出す")
  void entriesReturnEveryTypeWithSignedAmounts() {
    Mockito.when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
    Mockito.when(pointEntryRepository.findMemberEntryViews(Mockito.eq(MEMBER_ID), Mockito.any()))
        .thenReturn(
            List.of(
                new View(
                    2L,
                    OffsetDateTime.parse("2026-08-11T20:00:00+09:00"),
                    PointEntryType.USE,
                    -300,
                    null,
                    "店舗B"),
                new View(
                    1L,
                    OffsetDateTime.parse("2026-08-10T20:00:00+09:00"),
                    PointEntryType.MANUAL_ADJUST,
                    500,
                    LocalDate.parse("2027-08-10"),
                    "店舗A")));

    CursorPage<MemberPointEntryResponse> page = service.entries(EMAIL, null, 20);

    assertThat(page.content())
        .extracting(
            MemberPointEntryResponse::occurredOn,
            MemberPointEntryResponse::storeName,
            MemberPointEntryResponse::entryType,
            MemberPointEntryResponse::amount,
            MemberPointEntryResponse::expiresOn)
        .containsExactly(
            tuple(LocalDate.parse("2026-08-11"), "店舗B", "USE", -300, null),
            tuple(
                LocalDate.parse("2026-08-10"),
                "店舗A",
                "MANUAL_ADJUST",
                500,
                LocalDate.parse("2027-08-10")));
    assertThat(page.nextCursor()).as("続きが無ければ位置は返さない").isNull();
  }

  @Test
  @DisplayName("記帳時刻は業務のタイムゾーンで日付へ畳む")
  void foldsTheTimestampInTheBusinessTimezone() {
    // UTC で判じると前日になる時刻。JVM のタイムゾーンに引きずられると深夜の記帳が 1 日ずれて並ぶ。
    Mockito.when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
    Mockito.when(pointEntryRepository.findMemberEntryViews(Mockito.eq(MEMBER_ID), Mockito.any()))
        .thenReturn(List.of(grant(1L, "2026-08-11T00:30:00+09:00")));

    assertThat(service.entries(EMAIL, null, 20).content())
        .extracting(MemberPointEntryResponse::occurredOn)
        .containsExactly(LocalDate.parse("2026-08-11"));
  }

  @Test
  @DisplayName("発生店舗を持たない仕訳でも行は落ちず、店舗名だけが欠ける")
  void keepsEntriesWithoutAnOriginatingStore() {
    Mockito.when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
    Mockito.when(pointEntryRepository.findMemberEntryViews(Mockito.eq(MEMBER_ID), Mockito.any()))
        .thenReturn(
            List.of(
                new View(
                    3L,
                    OffsetDateTime.parse("2026-08-11T20:00:00+09:00"),
                    PointEntryType.EXPIRE,
                    -100,
                    null,
                    null)));

    assertThat(service.entries(EMAIL, null, 20).content())
        .extracting(MemberPointEntryResponse::entryType, MemberPointEntryResponse::storeName)
        .containsExactly(tuple("EXPIRE", null));
  }

  @Test
  @DisplayName("上限より多く読めたときは、上限までを返して続きの位置を添える")
  void returnsACursorWhenMoreRowsExist() {
    Mockito.when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
    Mockito.when(
            pointEntryRepository.findMemberEntryViews(Mockito.eq(MEMBER_ID), limitCaptor.capture()))
        .thenReturn(
            List.of(
                grant(3L, "2026-08-12T20:00:00+09:00"),
                grant(2L, "2026-08-11T20:00:00+09:00"),
                grant(1L, "2026-08-10T20:00:00+09:00")));

    CursorPage<MemberPointEntryResponse> page = service.entries(EMAIL, null, 2);

    assertThat(limitCaptor.getValue().max()).as("続きの有無は 1 件多く取って判る").isEqualTo(3);
    assertThat(page.content()).hasSize(2);
    // 位置は一覧の並び（記帳時刻 + id）と同じ組。ずれると続きが手前へ戻るか行を飛ばす。
    assertThat(PageCursor.decode(page.nextCursor()))
        .isEqualTo(new PageCursor("2026-08-11T20:00+09:00", "2"));
  }

  @Test
  @DisplayName("続きの取得は渡された位置より後ろだけを、本人の一致つきで問い合わせる")
  void fetchesOnlyAfterTheGivenPosition() {
    Mockito.when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
    OffsetDateTime position = OffsetDateTime.parse("2026-08-11T20:00:00+09:00");
    Mockito.when(
            pointEntryRepository.findMemberEntryViewsAfter(
                Mockito.eq(MEMBER_ID), Mockito.eq(position), Mockito.eq(2L), Mockito.any()))
        .thenReturn(List.of(grant(1L, "2026-08-10T20:00:00+09:00")));

    CursorPage<MemberPointEntryResponse> page =
        service.entries(EMAIL, new PageCursor(position.toString(), "2").encode(), 20);

    assertThat(page.content()).hasSize(1);
    Mockito.verify(pointEntryRepository, Mockito.never())
        .findMemberEntryViews(Mockito.any(), Mockito.any());
  }

  @Test
  @DisplayName("壊れた位置は要求誤りとして撥ね、先頭から取り直さない")
  void rejectsAMalformedCursor() {
    assertThatThrownBy(() -> service.entries(EMAIL, "!!!not-base64!!!", 20))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("会員でない主体には台帳を開かない")
  void refusesANonMemberPrincipal() {
    Mockito.when(memberLookupService.findByPlatformUserId(PLATFORM_USER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.balance(EMAIL)).isInstanceOf(StaleSessionException.class);
    assertThatThrownBy(() -> service.entries(EMAIL, null, 20))
        .isInstanceOf(StaleSessionException.class);
  }

  @Test
  @DisplayName("認証主体のユーザーが存在しない場合は 401 系例外")
  void refusesAStalePrincipal() {
    Mockito.when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.balance(EMAIL)).isInstanceOf(StaleSessionException.class);
  }
}
