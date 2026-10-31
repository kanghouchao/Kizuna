package com.kizuna.user.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

  List<Permission> findByCodeIn(Set<String> codes);

  /**
   * 目録の 1 行を押さえる（PostgreSQL では {@code FOR NO KEY UPDATE}）。ROLE_MANAGE
   * 保持者をゼロにしない守衛の共有直列化点であり、母集団を減らしうる操作は <b>経路を問わず、母集団（ROLE_MANAGE を含むロール集合と保持者）を取り直す前に ROLE_MANAGE
   * の行をここで押さえる</b>。ロール行を押さえる経路はロール行の後、どの経路も利用者行の前に取る（順序は {@link RoleRepository#findByIdForUpdate}）。
   * 減らしうるかの発火判定を押さえる前のロール集合に依らせてはならない（集合は並行する追加で増える）。 増やすだけの操作は押さえない。後から足す停止経路も同じ前提に従うこと。
   *
   * <p>押さえる前に読んだ集合で数えると、ロール定義の編集と授権の変更が双方とも検査を通り母集団が 0 になる。押さえるのが 1 行なので、行使点が増えても待ちは環にならない。 実体でなく
   * id を返すのは、実体で受けるとロックの獲得が版の照合を伴うためである。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p.id from com.kizuna.user.domain.Permission p where p.code = :code")
  Optional<Long> lockIdByCode(@Param("code") String code);
}
