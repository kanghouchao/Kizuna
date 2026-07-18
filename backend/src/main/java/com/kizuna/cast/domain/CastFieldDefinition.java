package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/**
 * キャストのカスタムフィールド定義（店舗別動的属性のスキーマ）。
 *
 * <p>key はプログラム識別子でテナント内一意・不変（コンストラクタ/ビルダーでのみ設定、setter も apply も持たない）。 label/displayOrder/isPublic
 * のみ {@link CastFieldDefinitionPatch} で部分更新できる。
 */
@Entity
@Table(name = "t_cast_field_definitions")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CastFieldDefinition extends StoreScopedEntity {

  @Column(name = "key", nullable = false, updatable = false, length = 50)
  private String key;

  @Column(name = "label", nullable = false, length = 100)
  private String label;

  @Column(name = "display_order", nullable = false)
  private Integer displayOrder;

  @Column(name = "is_public", nullable = false)
  private Boolean isPublic;

  /** 部分更新コマンドを適用する。null のフィールドは変更しない。key は不変（Patch に含まれない）。 */
  public void apply(CastFieldDefinitionPatch patch) {
    if (patch.label() != null) {
      this.label = patch.label();
    }
    if (patch.displayOrder() != null) {
      this.displayOrder = patch.displayOrder();
    }
    if (patch.isPublic() != null) {
      this.isPublic = patch.isPublic();
    }
  }
}
