package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shift.api.dto.CastScheduleResponse;
import com.kizuna.shift.api.dto.ShiftMapper;
import com.kizuna.shift.domain.CastScheduleView;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CastScheduleServiceTest {

  @Mock private PlatformUserRepository platformUserRepository;
  @Mock private CastRepository castRepository;
  @Mock private ShiftRepository shiftRepository;
  @Mock private ShiftMapper shiftMapper;

  @InjectMocks private CastScheduleService service;

  private static final String EMAIL = "cast@kizuna.test";
  private static final LocalDate FROM = LocalDate.of(2026, 7, 19);
  private static final LocalDate TO = LocalDate.of(2026, 7, 25);

  private PlatformUser userWithId(long id) {
    PlatformUser user = mock(PlatformUser.class);
    when(user.getId()).thenReturn(id);
    return user;
  }

  @Test
  void myWeek_throwsWhenEmailUnknown() {
    when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.myWeek(EMAIL, FROM, TO))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("ユーザーが見つかりません");

    verifyNoInteractions(castRepository, shiftRepository, shiftMapper);
  }

  @Test
  void myWeek_throwsWhenToIsBeforeFrom() {
    assertThatThrownBy(() -> service.myWeek(EMAIL, TO, FROM))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("取得範囲が不正です");

    verifyNoInteractions(platformUserRepository, castRepository, shiftRepository, shiftMapper);
  }

  @Test
  void myWeek_throwsWhenSpanExceedsMaxDays() {
    LocalDate from = LocalDate.of(2026, 7, 1);
    LocalDate to = from.plusDays(32);

    assertThatThrownBy(() -> service.myWeek(EMAIL, from, to))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("取得範囲が不正です");

    verifyNoInteractions(platformUserRepository, castRepository, shiftRepository, shiftMapper);
  }

  @Test
  void myWeek_returnsEmptyWhenNoCastLinked() {
    PlatformUser user = userWithId(42L);
    when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(castRepository.findIdsByPlatformUserId(42L)).thenReturn(List.of());

    List<CastScheduleResponse> result = service.myWeek(EMAIL, FROM, TO);

    assertThat(result).isEmpty();
    verifyNoInteractions(shiftRepository, shiftMapper);
  }

  @Test
  void myWeek_mapsConfirmedSchedulesAcrossCastIds() {
    PlatformUser user = userWithId(42L);
    when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    List<String> castIds = List.of("cast-store-a", "cast-store-b");
    when(castRepository.findIdsByPlatformUserId(42L)).thenReturn(castIds);

    CastScheduleView view = mock(CastScheduleView.class);
    when(shiftRepository.findConfirmedSchedule(castIds, FROM, TO)).thenReturn(List.of(view));
    CastScheduleResponse response =
        CastScheduleResponse.builder().storeId(1L).storeName("店舗A").build();
    when(shiftMapper.toScheduleResponse(view)).thenReturn(response);

    List<CastScheduleResponse> result = service.myWeek(EMAIL, FROM, TO);

    assertThat(result).containsExactly(response);
    verify(shiftRepository).findConfirmedSchedule(eq(castIds), eq(FROM), eq(TO));
    verify(shiftRepository, never()).findByWorkDateBetween(FROM, TO);
  }
}
