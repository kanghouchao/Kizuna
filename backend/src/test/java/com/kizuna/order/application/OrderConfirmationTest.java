package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.order.api.dto.CustomerSelectionRequest;
import com.kizuna.order.api.dto.OrderApplicationConfirmationRequest;
import com.kizuna.order.api.dto.OrderCorrectionRequest;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.order.domain.OrderFeeLineDraft;
import com.kizuna.order.domain.OrderFeeLineKind;
import com.kizuna.order.domain.OrderQuote;
import com.kizuna.order.domain.OrderServiceAdoption;
import com.kizuna.shared.config.AppProperties;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class OrderConfirmationTest {
  private static final String ACTOR = "staff@kizuna.test";

  private OrderConfirmation confirmation;
  private AppProperties properties;

  @BeforeEach
  void setUp() {
    properties = new AppProperties();
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
  private static OrderQuote calculatedAt(OffsetDateTime adoptedAt) {
    return Order.calculateNewWith(
        new OrderCourse("course", "revision", 1, "基本", 60, 12000, 0, "CURRENT_SETTING", adoptedAt),
        List.of(),
        List.of(
            new OrderFeeLineDraft(
                null,
                OrderFeeLineKind.SURCHARGE,
                "加算",
                1000,
                null,
                0,
                new OrderServiceAdoption("s1", "r1", 2, "CURRENT_SETTING", adoptedAt))));
  }

  private static OrderConsent consent(OrderQuote calculated, int grantPoints, Long balance) {
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

  // 試算と確定は同じ要求本文を別の型経路で受けるため、本文が写される Command の署名で束ねる
  private void assertTokenBindsInput(
      String operation, Function<String, Object> input, Function<String, Object> changedInput) {
    var consent = consent(calculatedAt(OffsetDateTime.now()), 130, 800L);
    String token = confirmation.sign(7L, operation, "id", ACTOR, input.apply(null), consent);

    assertThat(confirmation.wasPreviewed(7L, operation, "id", ACTOR, input.apply(token), token))
        .isTrue();
    assertThat(
            confirmation.wasPreviewed(7L, operation, "id", ACTOR, changedInput.apply(token), token))
        .isFalse();
  }

  @Test
  void tokenSignedAtPreviewProvesTheSameCreateRequest() {
    OrderMapper mapper = Mappers.getMapper(OrderMapper.class);
    Function<Integer, Function<String, Object>> request =
        pax ->
            token -> {
              var req = new OrderCreateRequest();
              req.setCourseId("course");
              req.setCastId("cast-1");
              req.setBusinessDate(LocalDate.parse("2026-10-28"));
              req.setCustomerSelection(
                  new CustomerSelectionRequest(CustomerSelectionRequest.Mode.NONE, null, null));
              req.setPax(pax);
              req.setConfirmationToken(token);
              return mapper.toCommand(req);
            };

    assertTokenBindsInput("CREATE", request.apply(2), request.apply(3));
  }

  @Test
  void tokenSignedAtPreviewProvesTheSameUpdateRequest() {
    OrderMapper mapper = Mappers.getMapper(OrderMapper.class);
    Function<Integer, Function<String, Object>> request =
        pax ->
            token -> {
              var req = new OrderUpdateRequest();
              req.setExpectedVersion(4L);
              req.setPax(pax);
              req.setConfirmationToken(token);
              return mapper.toCommand(req);
            };

    assertTokenBindsInput("UPDATE", request.apply(2), request.apply(3));
  }

  @Test
  void tokenSignedAtPreviewProvesTheSameApplicationConfirmationRequest() {
    OrderMapper mapper = Mappers.getMapper(OrderMapper.class);
    Function<Integer, Function<String, Object>> request =
        pax ->
            token -> {
              var req = new OrderApplicationConfirmationRequest();
              req.setCourseId("course");
              req.setBusinessDate(LocalDate.parse("2026-10-28"));
              req.setPax(pax);
              req.setConfirmationToken(token);
              return mapper.toCommand(req);
            };

    assertTokenBindsInput("CONFIRM", request.apply(2), request.apply(3));
  }

  @Test
  void tokenSignedAtPreviewProvesTheSameCorrectionRequest() {
    OrderMapper mapper = Mappers.getMapper(OrderMapper.class);
    Function<String, Function<String, Object>> request =
        reason ->
            token -> {
              var req = new OrderCorrectionRequest();
              req.setExpectedVersion(4L);
              req.setReason(reason);
              req.setFeeLines(List.of());
              req.setConfirmationToken(token);
              return mapper.toInput(req);
            };

    assertTokenBindsInput("CORRECT", request.apply("誤記"), request.apply("別の理由"));
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

  @Test
  void everyCommandExcludesItsTokenFromTheSignedInputWhateverTheNamingStrategy() {
    // 命名戦略に依らず、確認値を持たない試算と確認値つきの確定が同じ入力を署名する
    var camelCase = new OrderConfirmation(JsonMapper.builder().build(), properties);
    var tokens = List.of("t1", "t2");
    for (var confirmation : List.of(this.confirmation, camelCase)) {
      for (var inputs :
          List.<java.util.function.Function<String, Object>>of(
              token ->
                  new OrderCreateCommand(
                      null, null, token, "c", null, null, null, null, null, null, null, null, null,
                      "cast", 1, null, null, null, null, null, null),
              token ->
                  new OrderUpdateCommand(
                      null, token, "c", 1L, null, null, null, null, null, 1, null, null, null, null,
                      null, null, null, null, null, null),
              token ->
                  new OrderApplicationConfirmationCommand(
                      null, null, token, "c", null, null, null, null, null, null, 1, null, null,
                      null),
              token -> new OrderCorrectionInput(null, token, null, 1L, "理由", null, null, List.of()),
              token -> command(300, token))) {
        String issued =
            confirmation.sign(
                7L,
                "OP",
                "o1",
                ACTOR,
                inputs.apply(null),
                consent(calculatedAt(OffsetDateTime.now()), 130, 800L));
        for (var token : tokens) {
          assertThat(confirmation.wasPreviewed(7L, "OP", "o1", ACTOR, inputs.apply(token), issued))
              .isTrue();
        }
      }
    }
  }
}
