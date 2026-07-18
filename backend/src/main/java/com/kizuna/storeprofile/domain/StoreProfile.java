package com.kizuna.storeprofile.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "t_store_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Filter(name = "tenantFilter", condition = "store_id = :storeId")
public class StoreProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "store_id", nullable = false, unique = true, updatable = false)
  private Long storeId;

  @Column(name = "template_key", length = 50)
  @Builder.Default
  private String templateKey = "default";

  @Column(name = "logo_url", length = 500)
  private String logoUrl;

  @Column(name = "banner_url", length = 500)
  private String bannerUrl;

  @Column(name = "mv_url", length = 500)
  private String mvUrl;

  @Column(name = "mv_type", length = 20)
  @Builder.Default
  private String mvType = "image";

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "catch_copy", columnDefinition = "TEXT")
  private String catchCopy;

  @Column(name = "address", length = 500)
  private String address;

  @Column(name = "phone", length = 50)
  private String phone;

  @Column(name = "business_hours", length = 500)
  private String businessHours;

  @Column(name = "pricing_description", columnDefinition = "TEXT")
  private String pricingDescription;

  @Type(JsonBinaryType.class)
  @Column(name = "custom_texts", columnDefinition = "jsonb")
  @Builder.Default
  private Map<String, String> customTexts = new HashMap<>();

  @Type(JsonBinaryType.class)
  @Column(name = "sns_links", columnDefinition = "jsonb")
  @Builder.Default
  private List<SnsLink> snsLinks = new ArrayList<>();

  @Type(JsonBinaryType.class)
  @Column(name = "partner_links", columnDefinition = "jsonb")
  @Builder.Default
  private List<PartnerLink> partnerLinks = new ArrayList<>();

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** 楽観ロック用バージョン（全実体共通 — #400）。 */
  @Setter(AccessLevel.NONE) // 新規 public setter 禁止規約: バージョンは JPA が管理し外部から設定させない（#400）
  @Version
  @Column(nullable = false)
  private Long version;

  @PrePersist
  protected void onCreate() {
    var now = OffsetDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = OffsetDateTime.now();
  }

  /**
   * テナント用のデフォルト設定を生成する。
   *
   * @param storeId 対象テナントの ID
   * @return デフォルト値が設定された StoreProfile インスタンス
   */
  public static StoreProfile createDefault(Long storeId) {
    return StoreProfile.builder()
        .storeId(storeId)
        .templateKey("default")
        .mvType("image")
        .snsLinks(new ArrayList<>())
        .partnerLinks(new ArrayList<>())
        .build();
  }
}
