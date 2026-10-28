package com.kizuna.order.application;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OrderConfirmation {
  private final ObjectMapper json;
  private final AppProperties properties;

  /**
   * 試算の確認値。前半は入力の証明（操作・対象・店舗・実行者・入力）で、{@link #wasPreviewed} はこれだけを見る。後半は同意した内容まで含めた証明。
   * 実行者を含めるのは、他の操作者の試算を流用して確定させないため。
   */
  public String sign(
      Long storeId, String operation, String id, String actor, Object input, OrderConsent consent) {
    String inputProof = inputProof(storeId, operation, id, actor, input);
    return inputProof + "." + mac(List.of("order-confirmation-result-v3", inputProof, consent));
  }

  public boolean wasPreviewed(
      Long storeId, String operation, String id, String actor, Object input, String token) {
    if (token == null) return false;
    String[] parts = token.split("\\.", -1);
    return parts.length == 2
        && MessageDigest.isEqual(
            parts[0].getBytes(StandardCharsets.UTF_8),
            inputProof(storeId, operation, id, actor, input).getBytes(StandardCharsets.UTF_8));
  }

  // 確認値そのものは入力に含めない（試算は確認値なし、確定は確認値つきで同じ入力を送る）。各コマンドは確認値の成分を @JsonIgnore にしている。
  private String inputProof(Long storeId, String operation, String id, String actor, Object input) {
    return mac(List.of("order-confirmation-input-v3", operation, id, storeId, actor, input));
  }

  private String mac(Object payload) {
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
