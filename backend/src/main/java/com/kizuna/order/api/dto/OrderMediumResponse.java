package com.kizuna.order.api.dto;

/** 受注が知った媒体として指す媒体。名前は現在の媒体名。 */
public record OrderMediumResponse(String id, String name) {
  static OrderMediumResponse of(String id, String name) {
    return id == null ? null : new OrderMediumResponse(id, name);
  }
}
