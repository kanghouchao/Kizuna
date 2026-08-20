package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.domain.Cast;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.order.api.dto.MemberOrderApplicationCreateRequest;
import com.kizuna.order.api.dto.MemberOrderApplicationResponse;
import com.kizuna.order.domain.InvalidOrderApplicationOperationException;
import com.kizuna.order.domain.MemberOrderApplicationView;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class MemberOrderApplicationServiceTest {

  private static final long STORE_ID = 1L;
  private static final long OTHER_STORE_ID = 2L;
  private static final long MEMBER_ID = 100L;
  private static final long PLATFORM_USER_ID = 50L;
  private static final String EMAIL = "member@kizuna.test";
  private static final String MEMBER_CODE = "123456789012";
  private static final String DECLARED_NAME = "名乗り太郎";
  private static final String TIMEZONE = "Asia/Tokyo";

  @Mock OrderApplicationRepository orderApplicationRepository;
  @Mock OrderApplicationIntake orderApplicationIntake;
  @Mock PlatformUserRepository platformUserRepository;
  @Mock MemberLookupService memberLookupService;
  @Mock StoreExistenceCheck storeExistenceCheck;
  @Mock BusinessDateService businessDateService;

  @InjectMocks MemberOrderApplicationService service;

  @Captor ArgumentCaptor<OrderApplication> applicationCaptor;

  @BeforeEach
  void stubAuthenticatedMember() {
    lenient()
        .when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of(TIMEZONE)));
    PlatformUser user =
        PlatformUser.builder()
            .email(EMAIL)
            .password("pw")
            .displayName("会員")
            .enabled(true)
            .userType(UserType.MEMBER)
            .roleIds(Set.of())
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of())
            .build();
    user.setId(PLATFORM_USER_ID);
    lenient().when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    lenient()
        .when(memberLookupService.findByPlatformUserId(PLATFORM_USER_ID))
        .thenReturn(Optional.of(new MemberLookup(MEMBER_ID, MEMBER_CODE)));
  }

  private LocalDate today() {
    return LocalDate.now(ZoneId.of(TIMEZONE));
  }

  private MemberOrderApplicationCreateRequest requestFor(LocalDate date, String castId) {
    MemberOrderApplicationCreateRequest request = new MemberOrderApplicationCreateRequest();
    request.setStoreId(STORE_ID);
    request.setBusinessDate(date);
    request.setPax(2);
    request.setCastId(castId);
    request.setDeclaredName(DECLARED_NAME);
    return request;
  }

  /**
   * 述語が「成立する」と答えたときに返るキャスト。
   *
   * <p>成立の条件そのもの（店舗一致・在籍中）を固定するのは {@link NominatableCastLookupTest} で、ここは空か否かの翻訳だけを見る。
   */
  private static Cast nominatable(String castId) {
    Cast cast = Cast.builder().name("さくら").status("ACTIVE").build();
    cast.setId(castId);
    cast.setStoreId(STORE_ID);
    return cast;
  }

  private void stubSavedView() {
    when(orderApplicationRepository.save(any(OrderApplication.class)))
        .thenAnswer(i -> i.getArgument(0));
    MemberOrderApplicationView view = mock(MemberOrderApplicationView.class);
    when(orderApplicationRepository.findMemberView(anyLong(), any())).thenReturn(Optional.of(view));
  }

  @Test
  @DisplayName("申請が予約申請の PENDING 行として、人数と申請会員を持って起きること")
  void requestCreatesPendingApplication() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(true);
    stubSavedView();

    service.request(EMAIL, requestFor(today(), null));

    verify(orderApplicationRepository).save(applicationCaptor.capture());
    OrderApplication saved = applicationCaptor.getValue();
    assertThat(saved.getStatus()).isEqualTo(OrderApplicationStatus.PENDING);
    assertThat(saved.getRequesterMemberId()).isEqualTo(MEMBER_ID);
    assertThat(saved.getRequesterMemberCode()).isEqualTo(MEMBER_CODE);
    assertThat(saved.getPax()).isEqualTo(2);
    assertThat(saved.getStoreId()).as("店舗文脈が無い経路でも店舗が確定していること").isEqualTo(STORE_ID);
    assertThat(saved.getOrderId()).as("申請時点では受注が生まれていないこと").isNull();
    assertThat(saved.getRequesterDeclaredName())
        .as("店舗へ名乗る名前が申請に残ること（確定時の自動整備が台帳行の氏名に使う）")
        .isEqualTo(DECLARED_NAME);
  }

  @Test
  @DisplayName("存在しない店舗への申請を拒否すること")
  void requestRejectsUnknownStore() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.request(EMAIL, requestFor(today(), null)))
        .isInstanceOf(ServiceException.class);
    verify(orderApplicationRepository, never()).save(any(OrderApplication.class));
  }

  @Test
  @DisplayName("希望内容の判定を共有の受け付け判定へ、申請先の店舗・希望日・指名のまま委ねること")
  void requestDelegatesTheRequestedVisitToTheSharedIntake() {
    // 判定そのものは OrderApplicationIntakeTest が固定する。ここで見るのは、会員の入口が
    // 自前の判定を持たず、申請先の店舗で共有の判定を通すことだけ
    when(storeExistenceCheck.exists(OTHER_STORE_ID)).thenReturn(true);
    stubSavedView();

    MemberOrderApplicationCreateRequest request = requestFor(today().plusDays(3), "cast-1");
    request.setStoreId(OTHER_STORE_ID);

    service.request(EMAIL, request);

    verify(orderApplicationIntake)
        .validateRequestedVisit(OTHER_STORE_ID, today().plusDays(3), "cast-1");
  }

  @Test
  @DisplayName("受け付け判定が撥ねた申請では行が起きないこと")
  void requestDoesNotWriteWhenTheSharedIntakeRejects() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(true);
    doThrow(new ServiceException("過去の日付は申請できません"))
        .when(orderApplicationIntake)
        .validateRequestedVisit(anyLong(), any(), any());

    assertThatThrownBy(() -> service.request(EMAIL, requestFor(today(), null)))
        .isInstanceOf(ServiceException.class);
    verify(orderApplicationRepository, never()).save(any(OrderApplication.class));
  }

  @Test
  @DisplayName("一覧は本人が申請した予約に限って引くこと")
  void listQueriesOwnApplicationsOnly() {
    when(orderApplicationRepository.findMemberViews(anyLong(), any(Limit.class)))
        .thenReturn(List.of());

    service.list(EMAIL, null, 20);

    verify(orderApplicationRepository).findMemberViews(eq(MEMBER_ID), any(Limit.class));
  }

  @Test
  @DisplayName("続きの取得でも申請者の一致を問い合わせに載せ続けること")
  void listKeepsTheRequesterPredicateWhenResumingFromACursor() {
    when(orderApplicationRepository.findMemberViewsAfter(
            anyLong(), any(), anyString(), any(Limit.class)))
        .thenReturn(List.of());

    service.list(EMAIL, new PageCursor("2026-08-04", "a1").encode(), 20);

    // カーソルは位置を指すだけで隔離境界ではない。ここが抜けると他会員の申請に続きから到達できる。
    verify(orderApplicationRepository)
        .findMemberViewsAfter(
            eq(MEMBER_ID), eq(LocalDate.parse("2026-08-04")), eq("a1"), any(Limit.class));
  }

  @Test
  @DisplayName("続きがあるときは最後に返した申請を指すカーソルを添えること")
  void listHandsBackTheCursorOfTheLastReturnedApplication() {
    List<MemberOrderApplicationView> fetched =
        List.of(memberView("a1", "2026-08-04"), memberView("a2", "2026-08-03"));
    when(orderApplicationRepository.findMemberViews(anyLong(), eq(Limit.of(2))))
        .thenReturn(fetched);

    CursorPage<MemberOrderApplicationResponse> page = service.list(EMAIL, null, 1);

    assertThat(page.content()).hasSize(1);
    assertThat(PageCursor.decode(page.nextCursor())).isEqualTo(new PageCursor("2026-08-04", "a1"));
  }

  @Test
  @DisplayName("希望日を過ぎた PENDING は失効として返り、終端や当日以降の申請は失効でないこと")
  void listDerivesExpiryForStalePendingOnly() {
    MemberOrderApplicationView stale = memberView("a1", today().minusDays(1).toString());
    lenient().when(stale.getStatus()).thenReturn(OrderApplicationStatus.PENDING);
    MemberOrderApplicationView fresh = memberView("a2", today().toString());
    lenient().when(fresh.getStatus()).thenReturn(OrderApplicationStatus.PENDING);
    MemberOrderApplicationView declined = memberView("a3", today().minusDays(1).toString());
    lenient().when(declined.getStatus()).thenReturn(OrderApplicationStatus.DECLINED);
    when(orderApplicationRepository.findMemberViews(anyLong(), any(Limit.class)))
        .thenReturn(List.of(stale, fresh, declined));

    CursorPage<MemberOrderApplicationResponse> page = service.list(EMAIL, null, 20);

    assertThat(page.content())
        .extracting(MemberOrderApplicationResponse::isExpired)
        .containsExactly(true, false, false);
  }

  private static MemberOrderApplicationView memberView(String id, String businessDate) {
    MemberOrderApplicationView view = mock(MemberOrderApplicationView.class);
    lenient().when(view.getId()).thenReturn(id);
    lenient().when(view.getBusinessDate()).thenReturn(LocalDate.parse(businessDate));
    return view;
  }

  @Test
  @DisplayName("未処理の申請を本人が取り下げられ、実行者として本人が残ること")
  void withdrawTerminatesPendingApplication() {
    OrderApplication own =
        OrderApplication.builder()
            .status(OrderApplicationStatus.PENDING)
            .businessDate(today())
            .requesterMemberId(MEMBER_ID)
            .build();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(own));
    when(orderApplicationRepository.save(any(OrderApplication.class)))
        .thenAnswer(i -> i.getArgument(0));
    when(orderApplicationRepository.findMemberView(MEMBER_ID, "a1"))
        .thenReturn(Optional.of(mock(MemberOrderApplicationView.class)));

    service.withdraw(EMAIL, "a1");

    assertThat(own.getStatus()).isEqualTo(OrderApplicationStatus.WITHDRAWN);
    assertThat(own.getProcessedBy()).isEqualTo(PLATFORM_USER_ID);
  }

  @Test
  @DisplayName("確定・謝絶の後は本人でも取り下げられないこと")
  void withdrawRejectsProcessedApplication() {
    OrderApplication own =
        OrderApplication.builder()
            .status(OrderApplicationStatus.CONFIRMED)
            .businessDate(today())
            .requesterMemberId(MEMBER_ID)
            .build();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(own));

    assertThatThrownBy(() -> service.withdraw(EMAIL, "a1"))
        .isInstanceOf(InvalidOrderApplicationOperationException.class);
    assertThat(own.getStatus()).isEqualTo(OrderApplicationStatus.CONFIRMED);
    verify(orderApplicationRepository, never()).save(any(OrderApplication.class));
  }

  @Test
  @DisplayName("他人の申請は見つからないものとして扱うこと（存在を漏らさない）")
  void withdrawTreatsOthersApplicationAsNotFound() {
    OrderApplication others =
        OrderApplication.builder()
            .status(OrderApplicationStatus.PENDING)
            .businessDate(today())
            .requesterMemberId(999L)
            .build();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(others));

    assertThatThrownBy(() -> service.withdraw(EMAIL, "a1")).isInstanceOf(NotFoundException.class);
    assertThat(others.getStatus()).isEqualTo(OrderApplicationStatus.PENDING);
    verify(orderApplicationRepository, never()).save(any(OrderApplication.class));
  }

  @Test
  @DisplayName("申請者会員が欠落した申請（会員行の削除後）は本人経路から取り下げられないこと")
  void withdrawTreatsDetachedApplicationAsNotFound() {
    OrderApplication detached =
        OrderApplication.builder()
            .status(OrderApplicationStatus.PENDING)
            .businessDate(today())
            .build();
    when(orderApplicationRepository.findById("a1")).thenReturn(Optional.of(detached));

    assertThatThrownBy(() -> service.withdraw(EMAIL, "a1")).isInstanceOf(NotFoundException.class);
    verify(orderApplicationRepository, never()).save(any(OrderApplication.class));
  }

  @Test
  @DisplayName("会員でない主体は本人経路を使えないこと")
  void rejectsNonMemberPrincipal() {
    when(memberLookupService.findByPlatformUserId(PLATFORM_USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.list(EMAIL, null, 20))
        .isInstanceOf(StaleSessionException.class);
    verify(orderApplicationRepository, never()).findMemberViews(anyLong(), any(Limit.class));
  }

  @Test
  @DisplayName("認証主体が実在しない場合は失効セッションとして扱うこと")
  void rejectsUnknownPrincipal() {
    when(platformUserRepository.findByEmail(anyString())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.list("ghost@kizuna.test", null, 20))
        .isInstanceOf(StaleSessionException.class);
  }
}
