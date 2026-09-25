package com.kizuna.customer.application;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
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
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class MergeConfirmation {
  private final ObjectMapper json;
  private final AppProperties properties;
  private final StoreContext store;

  public String sign(Object state) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(
              mac.doFinal(
                  json.writeValueAsBytes(List.of("customer-merge-v1", store.getStoreId(), state))));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("統合の確認値を作成できません", ex);
    }
  }

  public void verify(String received, String expected) {
    if (received == null || !received.matches("[A-Za-z0-9_-]{43}"))
      throw new ServiceException("統合プレビューの確認値が不正です");
    if (expected == null
        || !MessageDigest.isEqual(
            received.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8)))
      throw new ConflictException("関連情報が変更されました。入力を保持して再度プレビューを確認してください");
  }
}
