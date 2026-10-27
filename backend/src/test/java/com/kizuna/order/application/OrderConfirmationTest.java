package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.order.api.dto.OrderPreviewResponse;
import com.kizuna.order.domain.OrderFeeLineKind;
import com.kizuna.shared.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class OrderConfirmationTest {
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
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("staff@kizuna.test", null));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
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

  @Test
  void tokenSignedAtPreviewProvesTheSameCommandAtCompletion() {
    // 試算は確認値なし、完了は確認値つきで同じ型を送る。確認値が署名の入力から外れないと両者は一致しない
    String token =
        confirmation.sign(
            7L,
            "COMPLETE",
            "o1",
            command(300, null),
            new OrderPreviewResponse(
                null, null, List.of(), 12000, 12000, 60, 0, null, List.of(), false, 0, List.of()));

    assertThat(confirmation.wasPreviewed(7L, "COMPLETE", "o1", command(300, token), token))
        .isTrue();
    assertThat(confirmation.wasPreviewed(7L, "COMPLETE", "o1", command(400, token), token))
        .isFalse();
  }
}
