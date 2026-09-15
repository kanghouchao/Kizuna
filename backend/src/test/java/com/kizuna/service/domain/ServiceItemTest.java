package com.kizuna.service.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ServiceItemTest {
  @Test
  void namesPreserveConsentTermsButFinancialChangesRequireConfirmation() {
    var item =
        ServiceItem.create(
            new ServiceTerms(ServiceKind.SPECIAL_SERVICE, "追加", null, ChargeType.PAID, 2000, 1500));
    item.replace(
        new ServiceTerms(ServiceKind.SPECIAL_SERVICE, "新名称", null, ChargeType.PAID, 2000, 1500), 1);
    assertThat(item.getTermsVersion()).isEqualTo(1);
    item.replace(
        new ServiceTerms(ServiceKind.SPECIAL_SERVICE, "新名称", null, ChargeType.PAID, 3000, 1500), 2);
    assertThat(item.getTermsVersion()).isEqualTo(2);
    item.replace(
        new ServiceTerms(ServiceKind.SPECIAL_SERVICE, "新名称", null, ChargeType.FREE, 0, 0), 3);
    assertThat(item.getTermsVersion()).isEqualTo(3);
    item.replace(
        new ServiceTerms(ServiceKind.SPECIAL_SERVICE, "有料", null, ChargeType.PAID, 3000, 1500), 4);
    item.replace(
        new ServiceTerms(ServiceKind.SPECIAL_SERVICE, "有料", null, ChargeType.PAID, 3000, 1600), 5);
    assertThat(item.getTermsVersion()).isEqualTo(5);
  }

  @Test
  void courseKeepsIntegerPriceAndRemuneration() {
    var item =
        ServiceItem.create(new ServiceTerms(ServiceKind.COURSE, " 基本 ", 60, null, 12000, 7000));
    assertThat(item.getTerms().getName()).isEqualTo("基本");
    assertThat(item.getTerms().getPrice()).isEqualTo(12000);
    assertThat(item.getTerms().getRemuneration()).isEqualTo(7000);
    assertThat(item.getRevisionNumber()).isEqualTo(1);
    assertThatThrownBy(() -> new ServiceTerms(ServiceKind.COURSE, "基本", 60, null, 12000, 12001))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void revisionsOnlyAdvanceForChangesAndDeletionIsTerminal() {
    var first = new ServiceTerms(ServiceKind.COURSE, "基本", 60, null, 12000, 7000);
    var item = ServiceItem.create(first);
    assertThat(item.replace(first, 1)).isFalse();
    assertThat(item.getRevisionNumber()).isEqualTo(1);
    var next = new ServiceTerms(ServiceKind.COURSE, "改定", 90, null, 15000, 9000);
    assertThat(item.replace(next, 1)).isTrue();
    assertThat(item.getRevisionNumber()).isEqualTo(2);
    assertThatThrownBy(() -> item.replace(first, 1)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> item.delete(1)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(
            () -> item.replace(new ServiceTerms(ServiceKind.SURCHARGE, "加算", null, null, 1, 0), 2))
        .isInstanceOf(ServiceException.class);
    item.delete(2);
    assertThat(item.isDeleted()).isTrue();
    assertThat(item.getRevisionNumber()).isEqualTo(3);
    assertThat(item.getTerms()).isEqualTo(next);
    assertThatThrownBy(() -> item.delete(3)).isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> item.replace(first, 3)).isInstanceOf(ServiceException.class);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "COURSE,基本,1,NULL,1,0",
        "COURSE,基本,2147483647,NULL,2147483647,2147483647",
        "SPECIAL_SERVICE,無料,NULL,FREE,0,0",
        "SPECIAL_SERVICE,有料,NULL,PAID,1,1",
        "SURCHARGE,加算,NULL,NULL,1,0"
      },
      nullValues = "NULL")
  void validBoundaries(
      ServiceKind kind,
      String name,
      Integer minutes,
      ChargeType charge,
      Integer price,
      Integer remuneration) {
    assertThat(new ServiceTerms(kind, name, minutes, charge, price, remuneration).getPrice())
        .isEqualTo(price);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "NULL,基本,60,NULL,1,0", "COURSE,NULL,60,NULL,1,0", "COURSE,' ',60,NULL,1,0",
        "COURSE,基本,0,NULL,1,0", "COURSE,基本,NULL,NULL,1,0", "COURSE,基本,-1,NULL,1,0",
        "COURSE,基本,60,NULL,0,0", "COURSE,基本,60,NULL,NULL,0", "COURSE,基本,60,NULL,1,NULL",
        "COURSE,基本,60,NULL,1,-1", "COURSE,基本,60,NULL,-1,0", "COURSE,基本,60,NULL,1,2",
        "COURSE,基本,60,PAID,1,0", "SPECIAL_SERVICE,無料,NULL,FREE,1,0",
            "SPECIAL_SERVICE,無料,NULL,FREE,0,1",
        "SPECIAL_SERVICE,有料,NULL,PAID,0,0", "SPECIAL_SERVICE,有料,NULL,NULL,1,0",
            "SPECIAL_SERVICE,有料,1,PAID,1,0",
        "SURCHARGE,加算,1,NULL,1,0", "SURCHARGE,加算,NULL,FREE,0,0", "SURCHARGE,加算,NULL,NULL,0,0"
      },
      nullValues = "NULL")
  void rejectsInvalidConditions(
      ServiceKind kind,
      String name,
      Integer minutes,
      ChargeType charge,
      Integer price,
      Integer remuneration) {
    assertThatThrownBy(() -> new ServiceTerms(kind, name, minutes, charge, price, remuneration))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void rejectsLongNames() {
    assertThatThrownBy(() -> new ServiceTerms(ServiceKind.COURSE, "あ".repeat(256), 60, null, 1, 0))
        .isInstanceOf(ServiceException.class);
  }
}
