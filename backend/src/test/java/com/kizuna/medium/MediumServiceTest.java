package com.kizuna.medium;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.medium.application.MediumService;
import com.kizuna.medium.domain.Medium;
import com.kizuna.medium.domain.MediumRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.user.application.BusinessAudit;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class MediumServiceTest {
  MediumRepository media = mock(MediumRepository.class);
  BusinessAudit audit = mock(BusinessAudit.class);
  MediumService service = new MediumService(media, audit);

  @Test
  void deletingAReferencedMediumIsAConflictAndIsNotAudited() {
    var medium = Medium.builder().name("参照あり").build();
    ReflectionTestUtils.setField(medium, "version", 0L);
    when(media.findByIdAndStoreId("m1", 7L)).thenReturn(Optional.of(medium));
    doThrow(
            new DataIntegrityViolationException(
                "fk",
                new ConstraintViolationException("fk", null, "fk_t_advertising_costs_medium")))
        .when(media)
        .flush();

    assertThatThrownBy(() -> service.delete(7L, "m1", 0L))
        .isInstanceOf(ConflictException.class)
        .hasMessage("記録から参照されている媒体は削除できません。使わない場合は停止してください");
    verifyNoInteractions(audit);
  }
}
