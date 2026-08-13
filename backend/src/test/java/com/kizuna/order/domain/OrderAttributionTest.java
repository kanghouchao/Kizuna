package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderAttributionTest {

  private static final OffsetDateTime ATTRIBUTED_AT = OffsetDateTime.parse("2026-08-12T19:00:00Z");

  @Test
  @DisplayName("完了時の帰属は根拠 COMPLETION の有効な記録になり、会員コードを帰属時点のまま持つこと")
  void onCompletionIsAnActiveCompletionRecord() {
    OrderAttribution attribution =
        OrderAttribution.onCompletion("o1", 7L, "123456789012", ATTRIBUTED_AT);

    assertThat(attribution.getOrderId()).isEqualTo("o1");
    assertThat(attribution.getMemberId()).isEqualTo(7L);
    assertThat(attribution.getMemberCode()).isEqualTo("123456789012");
    assertThat(attribution.getSource()).isEqualTo(OrderAttributionSource.COMPLETION);
    assertThat(attribution.getAttributedAt()).isEqualTo(ATTRIBUTED_AT);
    assertThat(attribution.getStatus()).isEqualTo(OrderAttributionStatus.ACTIVE);
  }

  @Test
  @DisplayName("事後申領の帰属は根拠 RECEIPT_TOKEN の有効な記録になること")
  void onReceiptClaimIsAnActiveReceiptTokenRecord() {
    OrderAttribution attribution =
        OrderAttribution.onReceiptClaim("o1", 7L, "123456789012", ATTRIBUTED_AT);

    assertThat(attribution.getOrderId()).isEqualTo("o1");
    assertThat(attribution.getMemberId()).isEqualTo(7L);
    assertThat(attribution.getMemberCode()).isEqualTo("123456789012");
    assertThat(attribution.getSource()).isEqualTo(OrderAttributionSource.RECEIPT_TOKEN);
    assertThat(attribution.getStatus()).isEqualTo(OrderAttributionStatus.ACTIVE);
  }

  @Test
  @DisplayName("受注 ID の無い帰属は記録できないこと")
  void missingOrderIdIsRejected() {
    assertThatThrownBy(() -> OrderAttribution.onCompletion(" ", 7L, "123456789012", ATTRIBUTED_AT))
        .isInstanceOf(InvalidOrderAttributionException.class)
        .hasMessageContaining("受注 ID");
  }

  @Test
  @DisplayName("会員 ID の無い帰属は記録できないこと")
  void missingMemberIdIsRejected() {
    assertThatThrownBy(
            () -> OrderAttribution.onCompletion("o1", null, "123456789012", ATTRIBUTED_AT))
        .isInstanceOf(InvalidOrderAttributionException.class)
        .hasMessageContaining("会員 ID");
  }

  @Test
  @DisplayName("会員コードの無い帰属は記録できないこと（会員削除後に誰の来店か辿れなくなる）")
  void missingMemberCodeIsRejected() {
    assertThatThrownBy(() -> OrderAttribution.onCompletion("o1", 7L, " ", ATTRIBUTED_AT))
        .isInstanceOf(InvalidOrderAttributionException.class)
        .hasMessageContaining("会員コード");
  }

  @Test
  @DisplayName("帰属時刻の無い帰属は記録できないこと")
  void missingAttributedAtIsRejected() {
    assertThatThrownBy(() -> OrderAttribution.onCompletion("o1", 7L, "123456789012", null))
        .isInstanceOf(InvalidOrderAttributionException.class)
        .hasMessageContaining("帰属の日時");
  }
}
