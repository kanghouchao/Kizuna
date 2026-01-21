package com.kizuna.model.entity.central.security;

import com.kizuna.model.entity.central.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "central_permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CentralPermission extends BaseEntity {

  @Column(nullable = false, unique = true, length = 150)
  private String name;

  @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
  private Set<CentralRole> roles = new HashSet<>();
}
