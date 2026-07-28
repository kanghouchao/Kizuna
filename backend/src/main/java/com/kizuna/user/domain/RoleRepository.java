package com.kizuna.user.domain;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, Long> {

  Optional<Role> findByName(String name);

  /**
   * 指定した権限コードを含むロールの id 集合。呼び出し側はユーザーのロール集合とこの集合の共通部分の有無で権限保持を判定する。
   *
   * <p>ロールは権限を ID 集合（{@code @ElementCollection}）で持つため、コードから id への解決を副問い合わせで挟む。
   */
  @Query(
      "select r.id from com.kizuna.user.domain.Role r join r.permissionIds pid"
          + " where pid in (select p.id from com.kizuna.user.domain.Permission p"
          + " where p.code = :code)")
  Set<Long> findIdsByPermissionCode(@Param("code") String code);
}
