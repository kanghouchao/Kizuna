package com.kizuna.model.entity.tenant;

import com.kizuna.model.dto.tenant.tenantconfig.PartnerLink;
import com.kizuna.model.dto.tenant.tenantconfig.SnsLink;
import com.kizuna.model.entity.central.tenant.Tenant;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "t_tenant_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "tenant")
public class TenantConfig {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false, unique = true)
  private Tenant tenant;

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
   * @param tenant 対象テナント
   * @return デフォルト値が設定された TenantConfig インスタンス
   */
  public static TenantConfig createDefault(Tenant tenant) {
    return TenantConfig.builder()
        .tenant(tenant)
        .templateKey("default")
        .mvType("image")
        .snsLinks(new ArrayList<>())
        .partnerLinks(new ArrayList<>())
        .build();
  }
}
