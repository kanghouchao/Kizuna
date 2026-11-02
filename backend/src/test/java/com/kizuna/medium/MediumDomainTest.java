package com.kizuna.medium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.medium.domain.Medium;
import com.kizuna.medium.domain.MediumStatus;
import com.kizuna.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MediumDomainTest {

  @Test
  void nameKeepsCaseAndWidthButDropsSurroundingWhitespaceIncludingFullWidth() {
    assertThat(Medium.normalizeName("　 Ｈｅａｖｅｎ ﾍﾌﾞﾝ \t")).isEqualTo("Ｈｅａｖｅｎ ﾍﾌﾞﾝ");
    assertThat(Medium.normalizeName(null)).isNull();
  }

  @Test
  void newMediumIsActiveAndStopResumeRejectRepeats() {
    var medium = Medium.builder().name("ヘブン").build();
    assertThat(medium.getStatus()).isEqualTo(MediumStatus.ACTIVE);

    assertThatThrownBy(medium::resume).isInstanceOf(ConflictException.class);
    medium.stop();
    assertThat(medium.getStatus()).isEqualTo(MediumStatus.STOPPED);
    assertThatThrownBy(medium::stop).isInstanceOf(ConflictException.class);
    medium.resume();
    assertThat(medium.getStatus()).isEqualTo(MediumStatus.ACTIVE);
  }

  @Test
  void staleVersionIsAConflict() {
    var medium = Medium.builder().name("ヘブン").build();
    ReflectionTestUtils.setField(medium, "version", 2L);

    medium.requireVersion(2);
    assertThatThrownBy(() -> medium.requireVersion(1)).isInstanceOf(ConflictException.class);
  }
}
