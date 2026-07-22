package com.kizuna.storeprofile.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@Filter(name = "storeFilter", condition = "store_id = :storeId")
public class StoreProfile extends StoreScopedEntity {

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

  /**
   * 店舗用のデフォルト設定を生成する。
   *
   * <p>店舗登録（平台側・StoreContext なし）から呼ばれるため store_id を明示設定する。 StoreScopeStampListener はこの設定済み値を尊重する。
   *
   * @param storeId 対象店舗の ID
   * @return デフォルト値が設定された StoreProfile インスタンス
   */
  public static StoreProfile createDefault(Long storeId) {
    StoreProfile profile =
        StoreProfile.builder()
            .templateKey("default")
            .mvType("image")
            .snsLinks(new ArrayList<>())
            .partnerLinks(new ArrayList<>())
            .build();
    profile.setStoreId(storeId);
    return profile;
  }
}
