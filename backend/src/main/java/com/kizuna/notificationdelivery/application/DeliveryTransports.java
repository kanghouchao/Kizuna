package com.kizuna.notificationdelivery.application;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.notification.transport.ContactTransport;
import com.kizuna.notificationdelivery.domain.DeliveryChannel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** チャネルから送信手段を引く登録簿。どのチャネルにも実装が一つあることを起動時に確かめる。 */
@Component
public class DeliveryTransports {
  private final Map<ContactType, ContactTransport> transports = new EnumMap<>(ContactType.class);

  public DeliveryTransports(List<ContactTransport> registered) {
    for (var transport : registered)
      if (transports.put(transport.type(), transport) != null)
        throw new IllegalStateException("連絡先種別の送信手段が重複しています: " + transport.type());
    for (var channel : DeliveryChannel.values())
      if (!transports.containsKey(channel.contactType()))
        throw new IllegalStateException("チャネルの送信手段がありません: " + channel);
  }

  public ContactTransport of(DeliveryChannel channel) {
    return transports.get(channel.contactType());
  }
}
