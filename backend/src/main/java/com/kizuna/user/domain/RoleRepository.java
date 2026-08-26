package com.kizuna.user.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, Long> {

  Optional<Role> findByName(String name);

  /**
   * 一覧用の要約（名称昇順）。集約を経由すると EAGER の権限集合が全ロール分ロードされるため、権限は group by の件数集計だけで済ませる。
   *
   * <p>権限なしのロールは不変条件上存在しないが、行を落とさないよう left join にしておく。
   */
  @Query(
      "select r.id as id, r.name as name, r.systemRole as systemRole,"
          + " count(pid) as permissionCount"
          + " from com.kizuna.user.domain.Role r left join r.permissionIds pid"
          + " group by r.id, r.name, r.systemRole order by r.name")
  List<RoleSummary> findAllSummaries();

  /**
   * 指定した権限コードのいずれかを含むロールの id 集合。呼び出し側はユーザーのロール集合とこの集合の共通部分の有無で権限保持を判定する。
   *
   * <p>ロールは権限を ID 集合（{@code @ElementCollection}）で持つため、コードから id への解決を副問い合わせで挟む。
   */
  @Query(
      "select r.id from com.kizuna.user.domain.Role r join r.permissionIds pid"
          + " where pid in (select p.id from com.kizuna.user.domain.Permission p"
          + " where p.code in :codes)")
  Set<Long> findIdsByPermissionCodeIn(@Param("codes") Collection<String> codes);

  /** 単一コード版。 */
  default Set<Long> findIdsByPermissionCode(String code) {
    return findIdsByPermissionCodeIn(Set.of(code));
  }

  /**
   * HQ 側ロール（構成権限に Console.PLATFORM の権限を 1 つ以上含むロール）の id 集合。管理面が共有する境界述語の単源で、判定を
   * 役職名（HQ_ADMIN）でなく権限構成で行うのは、管理が自作ロールへ移った配備でも同じ境界が成り立つようにするためである（ADR 0020）。
   */
  default Set<Long> findHqRoleIds() {
    return findIdsByPermissionCodeIn(PermissionCode.platformCodes());
  }

  /**
   * 店舗コンソールの入場資格になる権限（{@link PermissionCode#grantsStoreConsole()}）を 1 つ以上含むロールの id 集合。
   * ロール集合の権限並集が資格を含むことと、集合中のどれかがこの集合に属することは同値なので、授権側はこの共通部分の有無で判定できる。
   */
  default Set<Long> findStoreConsoleRoleIds() {
    return findIdsByPermissionCodeIn(PermissionCode.storeConsoleCodes());
  }
}
