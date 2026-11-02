package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ServiceException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderAcquisitionTest {
  @Test
  void mediaRequiresAMedium() {
    assertThatThrownBy(() -> new OrderAcquisition(AcquisitionSource.MEDIA, null))
        .isInstanceOf(ServiceException.class)
        .hasMessage("知った経路が媒体のときは媒体を選択してください");
    assertThat(new OrderAcquisition(AcquisitionSource.MEDIA, "m1").mediumId()).isEqualTo("m1");
  }

  @ParameterizedTest
  @EnumSource(value = AcquisitionSource.class, names = "MEDIA", mode = EnumSource.Mode.EXCLUDE)
  void otherRoutesRejectAMedium(AcquisitionSource source) {
    assertThatThrownBy(() -> new OrderAcquisition(source, "m1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("媒体は知った経路が媒体のときだけ指定できます");
    assertThat(new OrderAcquisition(source, null).source()).isEqualTo(source);
  }

  @Test
  void unsetRejectsAMediumAndAllowsBothEmpty() {
    assertThatThrownBy(() -> new OrderAcquisition(null, "m1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("媒体は知った経路が媒体のときだけ指定できます");
    assertThat(new OrderAcquisition(null, null)).isEqualTo(OrderAcquisition.UNSET);
  }

  @Test
  void patchReplacesTheRouteAndMediumTogetherAndNullKeepsThem() {
    var order =
        Order.placedByStore(
            1L,
            new NewStoreOrder(
                null,
                LocalDate.of(2026, 11, 1),
                null,
                null,
                null,
                null,
                1,
                ReceptionRoute.PHONE,
                null,
                new OrderAcquisition(AcquisitionSource.MEDIA, "m1"),
                null,
                null));
    assertThat(order.getAcquisition())
        .isEqualTo(new OrderAcquisition(AcquisitionSource.MEDIA, "m1"));

    order.apply(patch(null));
    assertThat(order.getAcquisition())
        .isEqualTo(new OrderAcquisition(AcquisitionSource.MEDIA, "m1"));

    order.apply(patch(new OrderAcquisition(AcquisitionSource.REPEAT, null)));
    assertThat(order.getAcquisitionSource()).isEqualTo(AcquisitionSource.REPEAT);
    assertThat(order.getMediumId()).isNull();
  }

  private static OrderPatch patch(OrderAcquisition acquisition) {
    return new OrderPatch(null, null, null, null, null, null, null, null, acquisition, null, null);
  }
}
