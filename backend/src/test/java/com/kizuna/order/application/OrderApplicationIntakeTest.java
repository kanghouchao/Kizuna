package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.cast.domain.Cast;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shift.application.ConfirmedShiftLookupService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 予約申請の受け付け判定の単体テスト。会員ポータルと公開店面の 2 つの入口が共有する述語をここで固定する。
 *
 * <p>入口ごとの単体テストではなくここで固定するのは、判定が入口に紐づいていないことを断言そのものが示すため。
 */
@ExtendWith(MockitoExtension.class)
class OrderApplicationIntakeTest {

  private static final long STORE_ID = 1L;
  private static final long OTHER_STORE_ID = 2L;
  private static final String TIMEZONE = "Asia/Tokyo";

  @Mock private NominatableCastLookup nominatableCast;
  @Mock private ConfirmedShiftLookupService confirmedShiftLookupService;
  @Mock private BusinessDateService businessDateService;

  @InjectMocks private OrderApplicationIntake intake;

  @BeforeEach
  void stubBusinessDate() {
    lenient()
        .when(businessDateService.currentBusinessDate())
        .thenReturn(LocalDate.now(ZoneId.of(TIMEZONE)));
  }

  private LocalDate today() {
    return LocalDate.now(ZoneId.of(TIMEZONE));
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

  @Test
  @DisplayName("過去日の申請を拒否すること")
  void rejectsPastDate() {
    assertThatThrownBy(() -> intake.validateRequestedVisit(STORE_ID, today().minusDays(1), null))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("候補照会と同じ上限（90 日）を超える先の申請を拒否すること")
  void rejectsDateBeyondLookaheadLimit() {
    // 指名なしの申請は候補照会（ConfirmedShiftLookupService.validateWorkDate）を経ないため、
    // 書き込み側で上限を見ないと無制限の未来日が素通りする
    assertThatThrownBy(() -> intake.validateRequestedVisit(STORE_ID, today().plusDays(91), null))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("範囲");
  }

  @Test
  @DisplayName("上限ちょうど（90 日先）の申請は受け付けること")
  void acceptsDateAtLookaheadLimit() {
    assertThatCode(() -> intake.validateRequestedVisit(STORE_ID, today().plusDays(90), null))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("指名先として成立しないキャストは、理由を区別せず「見つからない」として返すこと")
  void rejectsACastThatIsNotNominatable() {
    // 対象は店舗の外の申請者なので、他店舗・在籍停止・不在を区別しない — 区別すると、その id のキャストが
    // 当該店舗に在籍することそのものが分かってしまう（店舗スタッフ向けの OrderService は同じ述語から 400 を返す）。
    when(nominatableCast.find(STORE_ID, "cast-1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> intake.validateRequestedVisit(STORE_ID, today(), "cast-1"))
        .isInstanceOf(NotFoundException.class);
    // 候補に出さないだけでは、キャスト ID を直接送る要求を防げない
    verify(confirmedShiftLookupService, never())
        .hasPubliclyVisibleShift(anyLong(), anyString(), any());
  }

  @Test
  @DisplayName("指名の判定は申請先の店舗で行うこと")
  void checksTheNominationAgainstTheRequestedStore() {
    // 申請者は店舗を授権されず storeFilter も働かないため、店舗の一致は問い合わせ自体に載せるしかない
    when(nominatableCast.find(OTHER_STORE_ID, "cast-1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> intake.validateRequestedVisit(OTHER_STORE_ID, today(), "cast-1"))
        .isInstanceOf(NotFoundException.class);
    verify(nominatableCast).find(OTHER_STORE_ID, "cast-1");
  }

  @Test
  @DisplayName("指名検証が候補の読み口と同じ述語（公開可も見る）を使うこと")
  void validatesNominationThroughThePublicationGate() {
    when(nominatableCast.find(STORE_ID, "cast-1")).thenReturn(Optional.of(nominatable("cast-1")));
    when(confirmedShiftLookupService.hasPubliclyVisibleShift(STORE_ID, "cast-1", today()))
        .thenReturn(false);

    // 非公開を理由に別の文言へ分けない — 分けると隠したシフトの存在が読み取れてしまう
    assertThatThrownBy(() -> intake.validateRequestedVisit(STORE_ID, today(), "cast-1"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("指名したキャストはこの日の出勤予定がありません");
    verify(confirmedShiftLookupService, never()).hasConfirmedShift(anyLong(), anyString(), any());
  }

  @Test
  @DisplayName("公開された出勤予定のあるキャストは指名できること")
  void acceptsNominationBackedByAPubliclyVisibleShift() {
    when(nominatableCast.find(STORE_ID, "cast-1")).thenReturn(Optional.of(nominatable("cast-1")));
    when(confirmedShiftLookupService.hasPubliclyVisibleShift(STORE_ID, "cast-1", today()))
        .thenReturn(true);

    assertThatCode(() -> intake.validateRequestedVisit(STORE_ID, today(), "cast-1"))
        .doesNotThrowAnyException();
  }
}
