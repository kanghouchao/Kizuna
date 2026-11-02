package com.kizuna.medium.api.dto;

import com.kizuna.medium.domain.Medium;
import com.kizuna.medium.domain.MediumStatus;
import java.util.List;

public final class MediumResponses {
  private MediumResponses() {}

  /** 一覧は一店舗の媒体だけで、手で登録する件数に収まるためページを切らない。 */
  public record Items<T>(List<T> items) {}

  public record Created(String id) {}

  public record MediumSummary(String id, String name, MediumStatus status, Long version) {
    public static MediumSummary of(Medium medium) {
      return new MediumSummary(
          medium.getId(), medium.getName(), medium.getStatus(), medium.getVersion());
    }
  }

  public record MediumOption(String id, String name) {
    public static MediumOption of(Medium medium) {
      return new MediumOption(medium.getId(), medium.getName());
    }
  }
}
