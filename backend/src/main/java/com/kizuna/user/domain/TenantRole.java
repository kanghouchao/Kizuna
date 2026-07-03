package com.kizuna.user.domain;

import com.kizuna.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
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
    name = "t_roles",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_t_roles_tenant_name",
          columnNames = {"tenant_id", "name"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class TenantRole extends TenantScopedEntity {

  @Column(nullable = false, length = 100)
  private String name;

  @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
  private Set<StoreUser> users = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "t_role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  private Set<TenantPermission> permissions = new HashSet<>();
}
