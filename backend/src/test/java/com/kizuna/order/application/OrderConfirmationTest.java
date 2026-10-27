package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.order.domain.OrderFeeLineDraft;
import com.kizuna.order.domain.OrderFeeLineKind;
import com.kizuna.order.domain.OrderServiceAdoption;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.config.AppProperties;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class OrderConfirmationTest {
  private static final String ACTOR = "staff@kizuna.test";

  private OrderConfirmation confirmation;

  @BeforeEach
  void setUp() {
    var properties = new AppProperties();
    properties.getJwt().setSecret("order-confirmation-test-secret-0123456789");
    confirmation =
        new OrderConfirmation(
            JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build(),
            properties);
  }

  private static OrderCompletionCommand command(Integer usePoints, String token) {
    return new OrderCompletionCommand(
        4L,
        List.of(
            new OrderFeeLineInput(
                null, OrderFeeLineKind.CREDIT_SURCHARGE, "会計", 12000, null, null, null, null)),
        usePoints,
        token);
  }

  // 採用時刻は計算のたびに now() で打たれる。試算と確定は別の瞬間に計算する
  private static Order calculatedAt(OffsetDateTime adoptedAt) {
    Order order =
        Order.builder()
            .course(
                new OrderCourse(
                    "course", "revision", 1, "基本", 60, 12000, 0, "CURRENT_SETTING", adoptedAt))
            .status(OrderStatus.CONFIRMED)
            .build();
    order.replaceStoreFeeLines(
        List.of(
            new OrderFeeLineDraft(
                null,
                OrderFeeLineKind.SURCHARGE,
                "加算",
                1000,
                null,
                0,
                new OrderServiceAdoption("s1", "r1", 2, "CURRENT_SETTING", adoptedAt))));
    return order;
  }

  private static OrderConsent consent(Order calculated, int grantPoints, Long balance) {
    return OrderConsent.of(
        calculated,
        new OrderPoints(true, balance, "123456789012", 100, 300, grantPoints),
        List.of(),
        List.of());
  }

  @Test
  void tokenSignedAtPreviewProvesTheSameCommandAtCompletion() {
    // 試算は確認値なし、完了は確認値つきで同じ型を送る。確認値が署名の入力から外れないと両者は一致しない
    var consent = consent(calculatedAt(OffsetDateTime.now()), 130, 800L);
    String token = confirmation.sign(7L, "COMPLETE", "o1", ACTOR, command(300, null), consent);

    assertThat(confirmation.wasPreviewed(7L, "COMPLETE", "o1", ACTOR, command(300, token), token))
        .isTrue();
    assertThat(confirmation.wasPreviewed(7L, "COMPLETE", "o1", ACTOR, command(400, token), token))
        .isFalse();
  }

  @Test
  void anotherOperatorCannotCommitThePreview() {
    var consent = consent(calculatedAt(OffsetDateTime.now()), 130, 800L);
    String token = confirmation.sign(7L, "COMPLETE", "o1", ACTOR, command(300, null), consent);

    assertThat(
            confirmation.wasPreviewed(
                7L, "COMPLETE", "o1", "other@kizuna.test", command(300, token), token))
        .isFalse();
    assertThatThrownBy(
            () ->
                confirmation.verify(
                    token,
                    confirmation.sign(
                        7L, "COMPLETE", "o1", "other@kizuna.test", command(300, token), consent)))
        .isInstanceOf(OrderConfirmationConflict.class);
  }

  @Test
  void consentIgnoresAdoptionTimesAndTheBalanceButNotWhatTheUserAgreedTo() {
    String previewed =
        confirmation.sign(
            7L,
            "COMPLETE",
            "o1",
            ACTOR,
            command(300, null),
            consent(calculatedAt(OffsetDateTime.now().minusMinutes(5)), 130, 800L));

    // 採用時刻と残高だけが違う再計算は、同意した内容が同じなので通る
    assertThatCode(
            () ->
                confirmation.verify(
                    previewed,
                    confirmation.sign(
                        7L,
                        "COMPLETE",
                        "o1",
                        ACTOR,
                        command(300, previewed),
                        consent(calculatedAt(OffsetDateTime.now()), 130, 500L))))
        .doesNotThrowAnyException();
    // 付与予定が変われば、利用者の見た内容と食い違うので撥ねる
    assertThatThrownBy(
            () ->
                confirmation.verify(
                    previewed,
                    confirmation.sign(
                        7L,
                        "COMPLETE",
                        "o1",
                        ACTOR,
                        command(300, previewed),
                        consent(calculatedAt(OffsetDateTime.now()), 120, 800L))))
        .isInstanceOf(OrderConfirmationConflict.class);
  }
}
