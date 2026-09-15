package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderPreviewResponse;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@RequiredArgsConstructor
public class OrderConfirmation {
  private final ObjectMapper json;
  private final AppProperties properties;
  private final StoreContext store;

  public String sign(String operation, String id, Object input, OrderPreviewResponse preview) {
    ObjectNode request = json.valueToTree(input);
    request.remove("confirmation_token");
    ObjectNode result = json.valueToTree(preview);
    result.remove("confirmation_token");
    if (result.get("points") instanceof ObjectNode points) points.remove("point_balance");
    if (result.get("special_services") != null) {
      for (var value : result.get("special_services")) {
        if (value instanceof ObjectNode special) {
          special.remove("adopted_at");
          special.remove("current_consent_status");
        }
      }
    }
    var payload =
        List.of(
            "order-confirmation-v1",
            operation,
            id,
            store.getStoreId(),
            SecurityContextHolder.getContext().getAuthentication().getName(),
            request,
            result);
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(json.writeValueAsBytes(payload)));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("確認値を作成できません", ex);
    }
  }

  public void verify(String received, String expected) {
    if (received == null || received.isBlank()) throw new ServiceException("試算した内容を確認してください");
    if (!MessageDigest.isEqual(
        received.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8)))
      throw new OrderConfirmationConflict("confirmation_token");
  }
}
