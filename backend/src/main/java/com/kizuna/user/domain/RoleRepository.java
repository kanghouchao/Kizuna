package com.kizuna.user.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, Long> {

  Optional<Role> findByName(String name);

  /**
   * ロールの権限集合を書き換える前に、そのロール行を他の何よりも先に押さえる。ロック順序はロール行 → 権限目録の ROLE_MANAGE 行 → 利用者行（id 昇順）で、
   * 付与側（{@link #lockIdsForGrant}）と揃えることで、保持者の取り出しと付与の確定が互いを待つ形に直列化され、待ちも環にならない。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from com.kizuna.user.domain.Role r where r.id = :id")
  Optional<Role> findByIdForUpdate(@Param("id") Long id);

  /**
   * 付与後のロール集合の全行（保持中のものも含む — 集合の保存は全行を入れ直す）を、利用者行を書く前に id 昇順で共有ロックする。権限集合の変更（{@link
   * #findByIdForUpdate}）と排他になり、付与は変更の確定を待つか、先に確定して変更側に保持者として拾われる。挿入の外部キー検査の KEY SHARE は
   * 非キー更新と衝突しないので代わりにならない。実体でなく id を返すのは、読み込み済みの実体へのロックが版の照合を伴うため。
   */
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("select r.id from com.kizuna.user.domain.Role r where r.id in :ids order by r.id")
  List<Long> lockIdsForGrant(@Param("ids") Collection<Long> ids);

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
}
