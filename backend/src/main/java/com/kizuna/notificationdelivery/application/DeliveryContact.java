package com.kizuna.notificationdelivery.application;

import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.notificationdelivery.domain.DeliverySourceType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 起点種別ごとの解決器の登録簿。種別の欠けや重複は起動時に拒否する。 */
@Component
public class DeliveryContact {
  private final Map<DeliverySourceType, DeliverySourceResolver> resolvers =
      new EnumMap<>(DeliverySourceType.class);

  public DeliveryContact(List<DeliverySourceResolver> registered) {
    for (var resolver : registered)
      if (resolvers.put(resolver.sourceType(), resolver) != null)
        throw new IllegalStateException("起点種別の解決器が重複しています: " + resolver.sourceType());
    for (var type : DeliverySourceType.values())
      if (!resolvers.containsKey(type)) throw new IllegalStateException("起点種別の解決器がありません: " + type);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public DeliveryRecipient resolve(Long storeId, DeliveryContent content) {
    return resolvers
        .get(content.sourceType())
        .resolve(storeId, content.sourceId(), content.channel().contactType(), content.purpose());
  }
}
