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
   * 目録の 1 行を押さえる（{@code SELECT ... FOR UPDATE}）。不減零（ADR 0020 の守衛 G5）の共有直列化点であり、母集団を減らしうる操作は
   * <b>経路を問わずまず ROLE_MANAGE の行をここで押さえてから</b>、母集団（ROLE_MANAGE を含むロール集合と保持者）を取り直す。
   * 後から足す停止経路も同じ前提に従うこと。
   *
   * <p>押さえる前に読んだ集合で数えると、ロール定義の編集と授権の変更が双方とも検査を通り母集団が 0 になる。押さえるのが 1 行なので、行使点が増えても待ちは環にならない —
   * 目録は外部キーの連鎖の最上流で、この向きは ADR 0016 に沿う。実体でなく id を返すのは、実体で受けるとロックの獲得が版の照合を伴うためである。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p.id from com.kizuna.user.domain.Permission p where p.code = :code")
  Optional<Long> lockIdByCode(@Param("code") String code);
}
