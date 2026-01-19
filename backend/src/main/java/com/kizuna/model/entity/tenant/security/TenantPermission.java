package com.kizuna.model.entity.tenant.security;

import com.kizuna.model.entity.tenant.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(
    name = "t_permissions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_t_permissions_tenant_name",
          columnNames = {"tenant_id", "name"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class TenantPermission extends BaseEntity {

  @Column(nullable = false, length = 150)
  private String name;

  @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
  private Set<TenantRole> roles = new HashSet<>();
}
