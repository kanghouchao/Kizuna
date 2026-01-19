package com.cms.model.entity.tenant.security;

import com.cms.model.entity.tenant.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = "roles")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class TenantUser extends BaseEntity {

  @Column(nullable = false, length = 150)
  private String nickname;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false)
  private Boolean enabled = true;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "t_user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<TenantRole> roles = new HashSet<>();
}
