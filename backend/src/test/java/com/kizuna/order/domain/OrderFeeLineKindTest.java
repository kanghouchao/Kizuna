package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderFeeLineKindTest {

  @Test
  @DisplayName("種別は八種の閉集合であること")
  void kinds_areTheClosedSetOfEight() {
    // ホテル代・交通費・釣銭は受注金額外の回収・精算項目であり、種別を持たないことで型から排除される
    assertThat(OrderFeeLineKind.values())
        .containsExactly(
            OrderFeeLineKind.BASE_COURSE,
            OrderFeeLineKind.EXTENSION,
            OrderFeeLineKind.SPECIAL_SERVICE,
            OrderFeeLineKind.SURCHARGE,
            OrderFeeLineKind.DISCOUNT,
            OrderFeeLineKind.POINT_REDEMPTION,
            OrderFeeLineKind.POINT_REDEMPTION_OFFSET,
            OrderFeeLineKind.CREDIT_SURCHARGE);
  }

  @Test
  @DisplayName("加算の種別は負値を、減算の種別は正値を許さずこと")
  void allows_followsTheSignConventionOfEachKind() {
    Arrays.stream(OrderFeeLineKind.values())
        .filter(kind -> !kind.isDeduction())
        .forEach(
            kind -> {
              assertThat(kind.allows(1)).as("%s は加算を許すこと", kind).isTrue();
              assertThat(kind.allows(-1)).as("%s は負値を許さないこと", kind).isFalse();
            });
    Arrays.stream(OrderFeeLineKind.values())
        .filter(OrderFeeLineKind::isDeduction)
        .forEach(
            kind -> {
              assertThat(kind.allows(-1)).as("%s は減算を許すこと", kind).isTrue();
              assertThat(kind.allows(1)).as("%s は正値を許さないこと", kind).isFalse();
            });
  }

  @Test
  @DisplayName("減算に固定された種別は割引とポイント利用の 2 つであること")
  void isDeduction_coversDiscountAndPointRedemption() {
    assertThat(Arrays.stream(OrderFeeLineKind.values()).filter(OrderFeeLineKind::isDeduction))
        .containsExactly(OrderFeeLineKind.DISCOUNT, OrderFeeLineKind.POINT_REDEMPTION);
  }

  @Test
  @DisplayName("システム専有はポイント利用とその相殺であること")
  void isSystemOwned_coversRedemptionAndOffset() {
    assertThat(Arrays.stream(OrderFeeLineKind.values()).filter(OrderFeeLineKind::isSystemOwned))
        .containsExactly(
            OrderFeeLineKind.POINT_REDEMPTION, OrderFeeLineKind.POINT_REDEMPTION_OFFSET);
  }

  @ParameterizedTest
  @EnumSource(OrderFeeLineKind.class)
  @DisplayName("表示値と保存値の往復で元の値へ戻ること")
  void displayedAndSignedAmounts_roundTrip(OrderFeeLineKind kind) {
    assertThat(kind.displayedAmountOf(kind.signedAmountOf(1200))).isEqualTo(1200);
    assertThat(kind.signedAmountOf(1200)).isEqualTo(kind.isDeduction() ? -1200 : 1200);
  }

  @Test
  @DisplayName("名称が空白の明細は撥ねられること")
  void of_requiresName() {
    assertThatThrownBy(() -> OrderFeeLine.of(OrderFeeLineKind.SURCHARGE, "  ", 100))
        .isInstanceOf(InvalidOrderFeeLineException.class);
    assertThatThrownBy(() -> OrderFeeLine.of(null, "オプション", 100))
        .isInstanceOf(InvalidOrderFeeLineException.class);
    assertThatThrownBy(() -> OrderFeeLine.of(OrderFeeLineKind.SURCHARGE, "あ".repeat(256), 100))
        .isInstanceOf(InvalidOrderFeeLineException.class);
  }
}
