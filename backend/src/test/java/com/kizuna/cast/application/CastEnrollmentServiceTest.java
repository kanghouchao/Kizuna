package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastEnrollmentSnapshot;
import com.kizuna.cast.domain.CastEnrollmentSnapshotRepository;
import com.kizuna.cast.domain.CastEnrollmentStatusHistoryRepository;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CastEnrollmentServiceTest {
  @Mock CastEnrollmentRepository enrollments;
  @Mock CastEnrollmentSnapshotRepository snapshots;
  @Mock CastEnrollmentStatusHistoryRepository histories;
  @Mock StoreRepository stores;
  @Mock BusinessAudit audit;
  @InjectMocks CastEnrollmentService service;

  @Test
  void fieldRemovalRecordsActorAndPreservesEveryFullPreviousMap() {
    var first =
        CastEnrollment.builder()
            .customFields(new HashMap<>(Map.of("memo", "一", "other", "保持")))
            .build();
    var second = CastEnrollment.builder().customFields(new HashMap<>(Map.of("memo", "二"))).build();
    var untouched = CastEnrollment.builder().customFields(Map.of("other", "対象外")).build();
    service.removeInternalField(List.of(first, second, untouched), "memo", 42L);

    var captured = ArgumentCaptor.forClass(CastEnrollmentSnapshot.class);
    verify(snapshots, times(2)).save(captured.capture());
    assertThat(captured.getAllValues())
        .allSatisfy(snapshot -> assertThat(snapshot.getActorId()).isEqualTo(42L));
    assertThat(captured.getAllValues())
        .extracting(CastEnrollmentSnapshot::getCustomFields)
        .containsExactly(Map.of("memo", "一", "other", "保持"), Map.of("memo", "二"));
    assertThat(first.getCustomFields()).containsExactlyEntriesOf(Map.of("other", "保持"));
    assertThat(second.getCustomFields()).isEmpty();
    assertThat(untouched.getCustomFields()).containsExactlyEntriesOf(Map.of("other", "対象外"));
  }

  @Test
  void fieldRemovalWithoutValuesDoesNotSaveSnapshots() {
    service.removeInternalField(List.of(CastEnrollment.builder().build()), "missing", 42L);
    verifyNoInteractions(snapshots);
  }
}
