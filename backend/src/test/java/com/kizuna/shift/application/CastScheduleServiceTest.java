package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shift.domain.CastScheduleView;
import com.kizuna.shift.domain.ShiftRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CastScheduleServiceTest {

  @Mock private CastEnrollmentRepository castRepository;
  @Mock private ShiftRepository shiftRepository;

  @InjectMocks private CastScheduleService service;

  private static final long USER_ID = 42L;
  private static final LocalDate FROM = LocalDate.of(2026, 7, 19);
  private static final LocalDate TO = LocalDate.of(2026, 7, 25);

  @Test
  void myWeek_throwsWhenToIsBeforeFrom() {
    assertThatThrownBy(() -> service.myWeek(USER_ID, TO, FROM))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("取得範囲が不正です");

    verifyNoInteractions(castRepository, shiftRepository);
  }

  @Test
  void myWeek_throwsWhenSpanExceedsMaxDays() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = from.plusDays(32);

    assertThatThrownBy(() -> service.myWeek(USER_ID, from, to))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("取得範囲が不正です");

    verifyNoInteractions(castRepository, shiftRepository);
  }

  @Test
  void myWeek_returnsEmptyWhenNoCastLinked() {
    when(castRepository.findIdsByUserId(42L)).thenReturn(List.of());

    List<CastScheduleView> result = service.myWeek(USER_ID, FROM, TO);

    assertThat(result).isEmpty();
    verifyNoInteractions(shiftRepository);
  }

  @Test
  void myWeek_mapsConfirmedSchedulesAcrossCastIds() {
    List<String> castIds = List.of("cast-store-a", "cast-store-b");
    when(castRepository.findIdsByUserId(42L)).thenReturn(castIds);

    CastScheduleView view = mock(CastScheduleView.class);
    when(shiftRepository.findConfirmedSchedule(castIds, FROM, TO)).thenReturn(List.of(view));
    List<CastScheduleView> result = service.myWeek(USER_ID, FROM, TO);

    assertThat(result).containsExactly(view);
    verify(shiftRepository).findConfirmedSchedule(eq(castIds), eq(FROM), eq(TO));
    verify(shiftRepository, never()).findByStoreIdAndWorkDateBetween(any(), any(), any());
  }
}
