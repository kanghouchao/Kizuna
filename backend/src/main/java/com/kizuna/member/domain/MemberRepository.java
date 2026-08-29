package com.kizuna.member.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

  Optional<Member> findByPlatformUserId(Long platformUserId);

  Optional<Member> findByMemberCode(String memberCode);

  boolean existsByMemberCode(String memberCode);

  /**
   * 会員の最小表現。実体ではなく projection で返すのは、同じ取引が後からランクの更新で行ロックを取るため — 実体を先に読み込むと {@link
   * #findByIdForUpdate} がロックの昇格になり、直列化したい並行更新が版の食い違いで落ちる。
   */
  Optional<MemberIdentityView> findIdentityByMemberCode(String memberCode);

  /** {@link #findIdentityByMemberCode} と同じ理由の projection。 */
  Optional<MemberIdentityView> findIdentityByPlatformUserId(Long platformUserId);

  /**
   * 昇格の判定と書き込みのために会員行を悲観排他ロック付きで引く。
   *
   * <p>昇格は「現在のランクを読み、上位なら書く」の読み書きで、同一会員への並行する付与が同じ現在値を観測すると 昇格が二度記録される。行を直列化すれば後続は先行の結果を読み直す。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from com.kizuna.member.domain.Member m where m.id = :id")
  Optional<Member> findByIdForUpdate(@Param("id") Long id);
}
