package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.order.api.dto.MemberVisitResponse;
import com.kizuna.order.domain.MemberVisitView;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.point.application.PointLedgerService;
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
import java.util.Map;
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
class MemberVisitServiceTest {

  private static final String EMAIL = "member@example.com";
  private static final long PLATFORM_USER_ID = 10L;
  private static final long MEMBER_ID = 7L;

  @Mock private PlatformUserRepository platformUserRepository;
  @Mock private MemberLookupService memberLookupService;
  @Mock private OrderAttributionRepository orderAttributionRepository;
  @Mock private PointLedgerService pointLedgerService;

  @InjectMocks private MemberVisitService service;

  @Captor private ArgumentCaptor<Limit> limitCaptor;

  @SuppressWarnings("unchecked")
  @Captor
  private ArgumentCaptor<List<String>> orderIdsCaptor;

  /** 来店 1 件の読み側 projection の代役。 */
  private record View(
      Long id,
      OffsetDateTime createdAt,
      String orderId,
      LocalDate visitedOn,
      String storeName,
      Integer pax,
      String castName)
      implements MemberVisitView {

    @Override
    public Long getId() {
      return id;
    }

    @Override
    public OffsetDateTime getCreatedAt() {
      return createdAt;
    }

    @Override
    public String getOrderId() {
      return orderId;
    }

    @Override
    public LocalDate getVisitedOn() {
      return visitedOn;
    }

    @Override
    public String getStoreName() {
      return storeName;
    }

    @Override
    public Integer getPax() {
      return pax;
    }

    @Override
    public String getCastName() {
      return castName;
    }
  }

  private static View visit(long id, String createdAt) {
    return new View(
        id,
        OffsetDateTime.parse(createdAt),
        "order-" + id,
        LocalDate.parse("2026-08-10"),
        "店舗A",
        2,
        "キャスト花子");
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
  @DisplayName("来店は店舗を跨いで新しい順に並び、獲得ポイントは台帳の付与から添える")
  void listsVisitsAcrossStoresNewestFirstWithGrantedPoints() {
    Mockito.when(
            orderAttributionRepository.findMemberVisitViews(Mockito.eq(MEMBER_ID), Mockito.any()))
        .thenReturn(
            List.of(
                new View(
                    2L,
                    OffsetDateTime.parse("2026-08-11T20:00:00+09:00"),
                    "order-b",
                    LocalDate.parse("2026-08-11"),
                    "店舗B",
                    3,
                    "キャスト太郎"),
                new View(
                    1L,
                    OffsetDateTime.parse("2026-08-10T20:00:00+09:00"),
                    "order-a",
                    LocalDate.parse("2026-08-10"),
                    "店舗A",
                    2,
                    null)));
    Mockito.when(pointLedgerService.grantedPointsByOrder(Mockito.any()))
        .thenReturn(Map.of("order-b", 120, "order-a", 80));

    CursorPage<MemberVisitResponse> page = service.list(EMAIL, null, 20);

    assertThat(page.content())
        .extracting(
            MemberVisitResponse::visitedOn,
            MemberVisitResponse::storeName,
            MemberVisitResponse::pax,
            MemberVisitResponse::castName,
            MemberVisitResponse::grantedPoints)
        .containsExactly(
            tuple(LocalDate.parse("2026-08-11"), "店舗B", 3, "キャスト太郎", 120),
            tuple(LocalDate.parse("2026-08-10"), "店舗A", 2, null, 80));
    assertThat(page.nextCursor()).as("続きが無ければ位置は返さない").isNull();
  }

  @Test
  @DisplayName("台帳に付与行の無い来店も落とさず、獲得ポイント 0 として出す")
  void keepsVisitsWithoutAnyGrant() {
    // 0 円完了は付与行を積まないが帰属記録は生まれる（帰属は来店可視性の事実でポイントとは独立）。
    Mockito.when(
            orderAttributionRepository.findMemberVisitViews(Mockito.eq(MEMBER_ID), Mockito.any()))
        .thenReturn(List.of(visit(1L, "2026-08-10T20:00:00+09:00")));
    Mockito.when(pointLedgerService.grantedPointsByOrder(Mockito.any())).thenReturn(Map.of());

    assertThat(service.list(EMAIL, null, 20).content())
        .extracting(MemberVisitResponse::storeName, MemberVisitResponse::grantedPoints)
        .containsExactly(tuple("店舗A", 0));
  }

  @Test
  @DisplayName("付与の問い合わせは 1 ページにつき 1 回で、返す行の分だけを引く")
  void asksTheLedgerOncePerPage() {
    // 行ごとに引くと来店が増えるほど問い合わせが線形に増える。続きの有無を判るための余分な 1 件は
    // 返さないので、その分の付与も引かない。
    Mockito.when(
            orderAttributionRepository.findMemberVisitViews(Mockito.eq(MEMBER_ID), Mockito.any()))
        .thenReturn(
            List.of(
                visit(3L, "2026-08-12T20:00:00+09:00"),
                visit(2L, "2026-08-11T20:00:00+09:00"),
                visit(1L, "2026-08-10T20:00:00+09:00")));
    Mockito.when(pointLedgerService.grantedPointsByOrder(orderIdsCaptor.capture()))
        .thenReturn(Map.of());

    service.list(EMAIL, null, 2);

    Mockito.verify(pointLedgerService, Mockito.times(1)).grantedPointsByOrder(Mockito.any());
    assertThat(orderIdsCaptor.getValue()).containsExactly("order-3", "order-2");
  }

  @Test
  @DisplayName("上限より多く読めたときは、上限までを返して続きの位置を添える")
  void returnsACursorWhenMoreRowsExist() {
    Mockito.when(
            orderAttributionRepository.findMemberVisitViews(
                Mockito.eq(MEMBER_ID), limitCaptor.capture()))
        .thenReturn(
            List.of(
                visit(3L, "2026-08-12T20:00:00+09:00"),
                visit(2L, "2026-08-11T20:00:00+09:00"),
                visit(1L, "2026-08-10T20:00:00+09:00")));
    Mockito.when(pointLedgerService.grantedPointsByOrder(Mockito.any())).thenReturn(Map.of());

    CursorPage<MemberVisitResponse> page = service.list(EMAIL, null, 2);

    assertThat(limitCaptor.getValue().max()).as("続きの有無は 1 件多く取って判る").isEqualTo(3);
    assertThat(page.content()).hasSize(2);
    // 位置は一覧の並び（帰属の作成時刻 + id）と同じ組。ずれると続きが手前へ戻るか行を飛ばす。
    assertThat(PageCursor.decode(page.nextCursor()))
        .isEqualTo(new PageCursor("2026-08-11T20:00+09:00", "2"));
  }

  @Test
  @DisplayName("続きの取得は渡された位置より後ろだけを、本人の一致つきで問い合わせる")
  void fetchesOnlyAfterTheGivenPosition() {
    OffsetDateTime position = OffsetDateTime.parse("2026-08-11T20:00:00+09:00");
    Mockito.when(
            orderAttributionRepository.findMemberVisitViewsAfter(
                Mockito.eq(MEMBER_ID), Mockito.eq(position), Mockito.eq(2L), Mockito.any()))
        .thenReturn(List.of(visit(1L, "2026-08-10T20:00:00+09:00")));
    Mockito.when(pointLedgerService.grantedPointsByOrder(Mockito.any())).thenReturn(Map.of());

    CursorPage<MemberVisitResponse> page =
        service.list(EMAIL, new PageCursor(position.toString(), "2").encode(), 20);

    assertThat(page.content()).hasSize(1);
    Mockito.verify(orderAttributionRepository, Mockito.never())
        .findMemberVisitViews(Mockito.any(), Mockito.any());
  }

  @Test
  @DisplayName("壊れた位置は要求誤りとして撥ね、先頭から取り直さない")
  void rejectsAMalformedCursor() {
    assertThatThrownBy(() -> service.list(EMAIL, "!!!not-base64!!!", 20))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("会員でない主体には来店履歴を開かない")
  void refusesANonMemberPrincipal() {
    Mockito.when(memberLookupService.findByPlatformUserId(PLATFORM_USER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.list(EMAIL, null, 20))
        .isInstanceOf(StaleSessionException.class);
  }

  @Test
  @DisplayName("認証主体のユーザーが存在しない場合は 401 系例外")
  void refusesAStalePrincipal() {
    Mockito.when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.list(EMAIL, null, 20))
        .isInstanceOf(StaleSessionException.class);
  }
}
