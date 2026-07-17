package com.kizuna.user.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 能力束集約（#382 / #398）。ロール語彙を使う場合、ロール=能力束の名前であり、固定されたロール一覧を作らない — 束は DB データとして追加でき、発版を要しない。
 *
 * <p>種子として既存 3 ロールを同値折込した「HQ管理者」「店長」「店舗スタッフ」を投入する。ユーザーへの授権は {@link PlatformUser} 側が束 ID
 * の集合で保持する（跨集約 ID 参照）。
 */
@Entity
@Table(name = "platform_capability_bundles")
@Getter
@NoArgsConstructor
public class CapabilityBundle extends BaseEntity {

  @Column(nullable = false, unique = true, length = 100)
  private String name;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "platform_capability_bundle_items",
      joinColumns = @JoinColumn(name = "bundle_id"))
  @Column(name = "capability", nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  private Set<Capability> capabilities = new HashSet<>();

  @Builder
  public CapabilityBundle(String name, Set<Capability> capabilities) {
    if (name == null || name.isBlank()) {
      throw new InvalidCapabilityBundleException("能力束の名称は必須です");
    }
    if (capabilities == null || capabilities.isEmpty()) {
      throw new InvalidCapabilityBundleException("能力束には少なくとも 1 つの能力が必要です");
    }
    this.name = name;
    this.capabilities = new HashSet<>(capabilities);
  }
}
