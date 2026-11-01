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
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MemberPointServiceTest {

  private static final String EMAIL = "member@example.com";
  private static final long USER_ID = 10L;
  private static final long MEMBER_ID = 7L;

  @Mock private UserRepository userRepository;
  @Mock private MemberLookupService memberLookupService;
  @Mock private PointEntryRepository pointEntryRepository;
  @Mock private PointLedgerService pointLedgerService;
  @Mock private AppProperties appProperties;

  @InjectMocks private MemberPointService service;

  private static final Pageable FIRST = PageRequest.of(0, 20);

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
    User user =
        User.builder()
            .email(EMAIL)
            .password("encoded")
            .displayName("会員 花子")
            .enabled(true)
            .userType(UserType.MEMBER)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of())
            .build();
    user.setId(USER_ID);
    Mockito.lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    Mockito.lenient()
        .when(memberLookupService.findByUserId(USER_ID))
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
            new PageImpl<>(
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
                        "店舗A"))));

    Page<MemberPointEntryResponse> page = service.entries(EMAIL, FIRST);

    assertThat(page.getContent())
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
  }

  @Test
  @DisplayName("記帳時刻は業務のタイムゾーンで日付へ畳む")
  void foldsTheTimestampInTheBusinessTimezone() {
    // UTC で判じると前日になる時刻。JVM のタイムゾーンに引きずられると深夜の記帳が 1 日ずれて並ぶ。
    Mockito.when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
    Mockito.when(pointEntryRepository.findMemberEntryViews(Mockito.eq(MEMBER_ID), Mockito.any()))
        .thenReturn(new PageImpl<>(List.of(grant(1L, "2026-08-11T00:30:00+09:00"))));

    assertThat(service.entries(EMAIL, FIRST).getContent())
        .extracting(MemberPointEntryResponse::occurredOn)
        .containsExactly(LocalDate.parse("2026-08-11"));
  }

  @Test
  @DisplayName("発生店舗を持たない仕訳でも行は落ちず、店舗名だけが欠ける")
  void keepsEntriesWithoutAnOriginatingStore() {
    Mockito.when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
    Mockito.when(pointEntryRepository.findMemberEntryViews(Mockito.eq(MEMBER_ID), Mockito.any()))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    new View(
                        3L,
                        OffsetDateTime.parse("2026-08-11T20:00:00+09:00"),
                        PointEntryType.EXPIRE,
                        -100,
                        null,
                        null))));

    assertThat(service.entries(EMAIL, FIRST).getContent())
        .extracting(MemberPointEntryResponse::entryType, MemberPointEntryResponse::storeName)
        .containsExactly(tuple("EXPIRE", null));
  }

  @Test
  @DisplayName("明細は本人の会員 ID と受け取った Pageable で問い合わせ、総件数をそのまま返す")
  void entriesAskForTheOwnMemberWithTheGivenPage() {
    Mockito.when(appProperties.getTimezone()).thenReturn("Asia/Tokyo");
    Pageable second = PageRequest.of(1, 2);
    Mockito.when(pointEntryRepository.findMemberEntryViews(MEMBER_ID, second))
        .thenReturn(new PageImpl<>(List.of(grant(1L, "2026-08-10T20:00:00+09:00")), second, 3));

    Page<MemberPointEntryResponse> page = service.entries(EMAIL, second);

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  @DisplayName("会員でない主体には台帳を開かない")
  void refusesANonMemberPrincipal() {
    Mockito.when(memberLookupService.findByUserId(USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.balance(EMAIL)).isInstanceOf(StaleSessionException.class);
    assertThatThrownBy(() -> service.entries(EMAIL, FIRST))
        .isInstanceOf(StaleSessionException.class);
  }

  @Test
  @DisplayName("認証主体のユーザーが存在しない場合は 401 系例外")
  void refusesAStalePrincipal() {
    Mockito.when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.balance(EMAIL)).isInstanceOf(StaleSessionException.class);
  }
}
