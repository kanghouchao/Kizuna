package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.advertising.application.AdvertisingInput;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingCost;
import com.kizuna.advertising.domain.AdvertisingValues;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdvertisingDomainTest {
  @Test
  void normalizesTextAndDistinguishesUnknownFromZeroWhenCopying() {
    var zero = new AdvertisingValues(AdvertisingCategory.SALES, " 媒体 ", " ", null, 0, 0);
    assertThat(zero.mediaName()).isEqualTo("媒体");
    assertThat(zero.agencyName()).isNull();
    assertThat(zero.inquiryCount()).isZero();
    assertThat(zero.copied().inquiryCount()).isNull();
    assertThat(zero.copied().amount()).isZero();
    var max =
        new AdvertisingValues(
            AdvertisingCategory.RECRUITMENT,
            "採用",
            "会社",
            "計画",
            Integer.MAX_VALUE,
            Integer.MAX_VALUE);
    assertThat(max.copied().amount()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void rejectsInvalidInputInsteadOfCoercingIt() {
    assertThatThrownBy(() -> new AdvertisingValues(null, "媒体", null, null, 0, 0))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () -> new AdvertisingValues(AdvertisingCategory.SALES, " ", null, null, 0, 0))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () -> new AdvertisingValues(AdvertisingCategory.SALES, "媒体", null, null, -1, 0))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () -> new AdvertisingValues(AdvertisingCategory.SALES, "媒体", null, null, 0, -1))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> AdvertisingValues.text("a".repeat(201), 200))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> AdvertisingValues.text("a\0b", 200))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> AdvertisingValues.reason(" ")).isInstanceOf(ServiceException.class);
    assertThat(AdvertisingValues.reason(" 修正 ")).isEqualTo("修正");
  }

  @Test
  void rejectsStaleVersionAndRetainsValuesOnDeletion() {
    var c =
        AdvertisingCost.builder()
            .month("2026-09")
            .values(new AdvertisingValues(AdvertisingCategory.SALES, "媒体", null, null, null, 100))
            .build();
    ReflectionTestUtils.setField(c, "version", 2L);
    c.requireVersion(2);
    assertThatThrownBy(() -> c.requireVersion(1)).isInstanceOf(ConflictException.class);
    c.replace(new AdvertisingValues(AdvertisingCategory.RECRUITMENT, "採用", null, null, 0, 200));
    c.delete();
    assertThat(c.isDeleted()).isTrue();
    assertThat(c.values().amount()).isEqualTo(200);
    assertThat(c.getRevision()).isEqualTo(3);
  }

  @Test
  void validatesMonthsAndBoundedPaging() {
    for (String month : new String[] {"0000-01", "2026-13", "2026-1", "+2026-01", "2026-02-01"})
      assertThatThrownBy(() -> AdvertisingInput.month(month)).isInstanceOf(ServiceException.class);
    assertThat(AdvertisingInput.month("0001-01")).isEqualTo("0001-01");
    assertThat(AdvertisingInput.page(1, 2000).getPageSize()).isEqualTo(100);
    assertThatThrownBy(() -> AdvertisingInput.page(-1, 20)).isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> AdvertisingInput.page(0, 0)).isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> AdvertisingInput.page(Integer.MAX_VALUE, 100))
        .isInstanceOf(ServiceException.class);
  }
}
