package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourses;
import com.kizuna.order.domain.OrderQuote;
import com.kizuna.order.domain.SpecialServiceSnapshot;
import com.kizuna.service.application.OrderServiceCatalog;
import com.kizuna.shared.config.AppProperties;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class OrderCalculationTest {
  private static final String ACTOR = "staff@kizuna.test";
  private static final Map<String, Object> INPUT = Map.of("course_id", "course");
  private static final SpecialServiceSnapshot SERVICE =
      new SpecialServiceSnapshot(
          "svc",
          "rev",
          1,
          1,
          "特殊",
          "PAID",
          1000,
          500,
          "ACCEPTED_TERMS",
          OffsetDateTime.now(),
          "enr",
          "evt",
          1L);

  private OrderSpecialServices specialServices;
  private OrderCalculation calculation;
  private final OrderQuote calculated =
      Order.calculateNewWith(OrderCourses.course("基本", 60, 12000), List.of(), List.of());

  @BeforeEach
  void setUp() {
    var properties = new AppProperties();
    properties.getJwt().setSecret("order-calculation-test-secret-0123456789");
    specialServices = mock(OrderSpecialServices.class);
    calculation =
        new OrderCalculation(
            mock(OrderServiceCatalog.class),
            new OrderConfirmation(
                JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .build(),
                properties),
            mock(OrderFeeLineSelection.class),
            specialServices);
    when(specialServices.describe(anyLong(), any(OrderQuote.class)))
        .thenReturn(List.of(new SpecialServiceStatus(SERVICE, false, "ACTIVE")));
  }

  private String previewToken() {
    return calculation
        .preview(7L, "UPDATE", "o1", ACTOR, INPUT, calculated, null)
        .confirmationToken();
  }

  @Test
  void commitMatchesThePreviewWithoutReadingTheConsentStatus() {
    // 同意の現況は表示だけの値。確定の照合は修復待ちの印だけを引き直す
    when(specialServices.attention(anyLong(), any(OrderQuote.class)))
        .thenReturn(List.of(new SpecialServiceStatus(SERVICE, false, null)));
    String token = previewToken();

    assertThatCode(
            () -> calculation.verify(token, 7L, "UPDATE", "o1", ACTOR, INPUT, calculated, null))
        .doesNotThrowAnyException();
    verify(specialServices).attention(7L, calculated);
  }

  @Test
  void aServiceRejectedAfterThePreviewSendsTheCommitBackToPreview() {
    // 更新の確定は修復待ちを撥ねる門を持たない。試算の後に拒否された特殊サービスは照合の食い違いで止める
    when(specialServices.attention(anyLong(), any(OrderQuote.class)))
        .thenReturn(List.of(new SpecialServiceStatus(SERVICE, true, null)));
    String token = previewToken();

    assertThatThrownBy(
            () -> calculation.verify(token, 7L, "UPDATE", "o1", ACTOR, INPUT, calculated, null))
        .isInstanceOf(OrderConfirmationConflict.class);
  }
}
