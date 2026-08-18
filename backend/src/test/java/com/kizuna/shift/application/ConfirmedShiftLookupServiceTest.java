package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shift.api.dto.PublicShiftResponse;
import com.kizuna.shift.domain.ConfirmedShiftCastView;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfirmedShiftLookupServiceTest {

  private static final long STORE_ID = 1L;
  private static final String TIMEZONE = "Asia/Tokyo";

  @Mock ShiftRepository shiftRepository;
  @Mock StoreExistenceCheck storeExistenceCheck;
  @Mock AppProperties appProperties;

  @InjectMocks ConfirmedShiftLookupService service;

  @BeforeEach
  void stubTimezone() {
    lenient().when(appProperties.getTimezone()).thenReturn(TIMEZONE);
  }

  private LocalDate today() {
    return LocalDate.now(ZoneId.of(TIMEZONE));
  }

  @Test
  @DisplayName("指定店舗・指定日の確定シフトのキャストを返すこと")
  void listConfirmedCastsMapsView() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(true);
    ConfirmedShiftCastView view = mock(ConfirmedShiftCastView.class);
    when(view.getCastId()).thenReturn("cast-1");
    when(view.getCastName()).thenReturn("さくら");
    when(view.getCastPhotoUrl()).thenReturn("https://example.test/p.png");
    when(view.getStartTime()).thenReturn(LocalTime.of(18, 0));
    when(view.getEndTime()).thenReturn(LocalTime.of(23, 0));
    when(shiftRepository.findConfirmedCasts(STORE_ID, today())).thenReturn(List.of(view));

    List<PublicShiftResponse> result = service.listConfirmedCasts(STORE_ID, today());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCastId()).isEqualTo("cast-1");
    assertThat(result.get(0).getCastName()).isEqualTo("さくら");
    assertThat(result.get(0).getStartTime()).isEqualTo(LocalTime.of(18, 0));
  }

  @Test
  @DisplayName("同じ日に複数の確定シフトを持つキャストは 1 件に畳むこと")
  void listConfirmedCastsCollapsesMultipleShiftsPerCast() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(true);
    ConfirmedShiftCastView early = mock(ConfirmedShiftCastView.class);
    when(early.getCastId()).thenReturn("cast-1");
    when(early.getCastName()).thenReturn("さくら");
    when(early.getStartTime()).thenReturn(LocalTime.of(12, 0));
    when(early.getEndTime()).thenReturn(LocalTime.of(15, 0));
    ConfirmedShiftCastView late = mock(ConfirmedShiftCastView.class);
    when(late.getCastId()).thenReturn("cast-1");
    when(shiftRepository.findConfirmedCasts(STORE_ID, today())).thenReturn(List.of(early, late));

    List<PublicShiftResponse> result = service.listConfirmedCasts(STORE_ID, today());

    // 指名はキャスト単位のため、重複させると同じ値の選択肢が並ぶだけになる
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCastId()).isEqualTo("cast-1");
    assertThat(result.get(0).getStartTime()).as("残るのは最も早い出勤").isEqualTo(LocalTime.of(12, 0));
  }

  @Test
  @DisplayName("存在しない店舗では照会せずに拒否すること")
  void listConfirmedCastsRejectsUnknownStore() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.listConfirmedCasts(STORE_ID, today()))
        .isInstanceOf(ServiceException.class);
    verify(shiftRepository, never()).findConfirmedCasts(anyLong(), any());
  }

  @Test
  @DisplayName("過去日・上限を超える未来日は拒否すること")
  void listConfirmedCastsRejectsOutOfRangeDates() {
    when(storeExistenceCheck.exists(STORE_ID)).thenReturn(true);

    assertThatThrownBy(() -> service.listConfirmedCasts(STORE_ID, today().minusDays(1)))
        .as("過去日")
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> service.listConfirmedCasts(STORE_ID, today().plusDays(91)))
        .as("上限を超える未来日")
        .isInstanceOf(ServiceException.class);
    verify(shiftRepository, never()).findConfirmedCasts(anyLong(), any());
  }

  @Test
  @DisplayName("確定シフトの有無を店舗・キャスト・日付・確定状態で問い合わせること")
  void hasConfirmedShiftQueriesWithStoreBoundary() {
    when(shiftRepository.existsByStoreIdAndCastIdAndWorkDateAndStatus(
            anyLong(), anyString(), any(), any()))
        .thenReturn(true);

    assertThat(service.hasConfirmedShift(STORE_ID, "cast-1", today())).isTrue();

    verify(shiftRepository)
        .existsByStoreIdAndCastIdAndWorkDateAndStatus(
            STORE_ID, "cast-1", today(), ShiftStatus.CONFIRMED);
  }
}
