package com.kizuna.model.entity.central;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "central_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
public class SystemConfig extends BaseEntity {

  @Column(name = "config_key", nullable = false, unique = true, length = 100)
  private String configKey;

  @Column(name = "config_value", columnDefinition = "TEXT")
  private String configValue;

  @Column(name = "category", length = 50)
  private String category;

  @Column(name = "description", length = 500)
  private String description;

  @Builder.Default
  @Column(name = "value_type", nullable = false, length = 20)
  private String valueType = "STRING";

  @Builder.Default
  @Column(name = "secret", nullable = false)
  private Boolean secret = false;
}
