package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderAttributionTest {

  private static final OffsetDateTime ATTRIBUTED_AT = OffsetDateTime.parse("2026-08-12T19:00:00Z");

  private static final OffsetDateTime INVALIDATED_AT = OffsetDateTime.parse("2026-08-20T10:00:00Z");

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

  @Test
  @DisplayName("無効化は理由・実行者・時刻を残し、帰属そのものの記録は書き換えないこと")
  void invalidateRecordsTheReasonActorAndTime() {
    OrderAttribution attribution =
        OrderAttribution.onCompletion("o1", 7L, "123456789012", ATTRIBUTED_AT);

    attribution.invalidate("別人の来店を取り違えたため", 42L, INVALIDATED_AT);

    assertThat(attribution.getStatus()).isEqualTo(OrderAttributionStatus.INVALIDATED);
    assertThat(attribution.getInvalidatedReason()).isEqualTo("別人の来店を取り違えたため");
    assertThat(attribution.getInvalidatedBy()).isEqualTo(42L);
    assertThat(attribution.getInvalidatedAt()).isEqualTo(INVALIDATED_AT);
    // 誰の来店として記録されていたかは訂正後も読めなければならない（監査の対象そのもの）
    assertThat(attribution.getMemberId()).isEqualTo(7L);
    assertThat(attribution.getMemberCode()).isEqualTo("123456789012");
    assertThat(attribution.getSource()).isEqualTo(OrderAttributionSource.COMPLETION);
    assertThat(attribution.getAttributedAt()).isEqualTo(ATTRIBUTED_AT);
  }

  @Test
  @DisplayName("理由の無い無効化は拒まれること（訂正の根拠が残らない）")
  void invalidateWithoutReasonIsRejected() {
    OrderAttribution attribution =
        OrderAttribution.onCompletion("o1", 7L, "123456789012", ATTRIBUTED_AT);

    assertThatThrownBy(() -> attribution.invalidate(" ", 42L, INVALIDATED_AT))
        .isInstanceOf(InvalidOrderAttributionException.class)
        .hasMessageContaining("無効化の理由");
    assertThat(attribution.getStatus()).isEqualTo(OrderAttributionStatus.ACTIVE);
  }

  @Test
  @DisplayName("実行者の無い無効化は拒まれること")
  void invalidateWithoutActorIsRejected() {
    OrderAttribution attribution =
        OrderAttribution.onCompletion("o1", 7L, "123456789012", ATTRIBUTED_AT);

    assertThatThrownBy(() -> attribution.invalidate("取り違え", null, INVALIDATED_AT))
        .isInstanceOf(InvalidOrderAttributionException.class)
        .hasMessageContaining("無効化の実行者");
  }

  @Test
  @DisplayName("無効化済みの記録は二度目の無効化を受け付けないこと（初回の理由と実行者が上書きされる）")
  void invalidatingTwiceIsRejected() {
    OrderAttribution attribution =
        OrderAttribution.onCompletion("o1", 7L, "123456789012", ATTRIBUTED_AT);
    attribution.invalidate("初回の理由", 42L, INVALIDATED_AT);

    assertThatThrownBy(() -> attribution.invalidate("二度目の理由", 43L, INVALIDATED_AT.plusDays(1)))
        .isInstanceOf(InvalidOrderAttributionException.class)
        .hasMessageContaining("既に無効化");
    assertThat(attribution.getInvalidatedReason()).isEqualTo("初回の理由");
    assertThat(attribution.getInvalidatedBy()).isEqualTo(42L);
  }
}
