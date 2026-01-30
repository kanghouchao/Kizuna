package com.kizuna.model.entity.tenant.menu;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "t_menus")
public class TenantMenu {

  @Id private String id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  @JsonIgnore
  private TenantMenu parent;

  @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
  @OrderBy("sortOrder ASC")
  private List<TenantMenu> children = new ArrayList<>();

  @Column(nullable = false)
  private String label;

  private String path;

  private String icon;

  private String permission;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
