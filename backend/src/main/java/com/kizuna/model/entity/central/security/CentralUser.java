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
@Table(name = "central_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CentralUser extends BaseEntity {

  @Column(nullable = false, unique = true, length = 150)
  private String username;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false)
  private Boolean enabled = true;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "central_user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<CentralRole> roles = new HashSet<>();
}
