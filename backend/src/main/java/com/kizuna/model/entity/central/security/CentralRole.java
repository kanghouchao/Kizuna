package com.kizuna.model.entity.central.security;

import com.kizuna.model.entity.central.BaseEntity;
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

@Entity
@Table(name = "central_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CentralRole extends BaseEntity {

  @Column(nullable = false, unique = true, length = 100)
  private String name;

  @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
  private Set<CentralUser> users = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "central_role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  private Set<CentralPermission> permissions = new HashSet<>();
}
