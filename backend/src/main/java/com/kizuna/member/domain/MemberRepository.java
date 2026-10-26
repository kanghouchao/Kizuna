package com.kizuna.member.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

  /** 台帳の新規仕訳が取る外部キーの KEY SHARE と競合させるため、NO KEY UPDATE ではなく FOR UPDATE を使う。 */
  @Query(value = "select id from t_members where id = :id for update", nativeQuery = true)
  Optional<Long> lockIdForBalanceConfirmation(Long id);

  Optional<Member> findByUserId(Long userId);

  /**
   * 退会のために会員行を悲観排他ロック付きで引く。退会は「退会済みか読み、未退会なら書く」の読み書きで、二重の退会と、 同じ会員への並行する帰属の物化（{@link
   * #lockIdForUpdate} を先に取る）を直列化する。取引内で最初の読み込みであること（ロックの昇格は版の照合を伴う）。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from com.kizuna.member.domain.Member m where m.userId = :userId")
  Optional<Member> findByUserIdForUpdate(@Param("userId") Long userId);

  /** 退会済みか。帰属の物化と巻き戻しの再消去が、会員行を読み込まずに分岐するための述語。 */
  @Query(
      "select count(m) > 0 from com.kizuna.member.domain.Member m"
          + " where m.id = :id and m.withdrawnAt is not null")
  boolean isWithdrawn(@Param("id") Long id);

  Optional<Member> findByMemberCode(String memberCode);

  boolean existsByMemberCode(String memberCode);

  /**
   * 会員の最小表現。実体ではなく projection で返すのは、同じ取引が後からランクの更新で行ロックを取るため — 実体を先に読み込むと {@link
   * #findByIdForUpdate} がロックの昇格になり、直列化したい並行更新が版の食い違いで落ちる。
   */
  Optional<MemberIdentityView> findIdentityByMemberCode(String memberCode);

  /** {@link #findIdentityByMemberCode} と同じ理由の projection。 */
  Optional<MemberIdentityView> findIdentityByUserId(Long userId);

  /**
   * 昇格の判定と書き込みのために会員行を悲観排他ロック付きで引く。
   *
   * <p>昇格は「現在のランクを読み、上位なら書く」の読み書きで、同一会員への並行する付与が同じ現在値を観測すると 昇格が二度記録される。行を直列化すれば後続は先行の結果を読み直す。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from com.kizuna.member.domain.Member m where m.id = :id")
  Optional<Member> findByIdForUpdate(@Param("id") Long id);

  /**
   * {@link #findByIdForUpdate} と同じ行ロックを、会員へ外部キーを張る書き込みより<b>前に</b>取る。挿入の外部キー検査は会員行へ FOR KEY SHARE
   * を置くため、先に書いてから FOR UPDATE を求めると、同じ会員への並行する 2 取引が互いの KEY SHARE を待って死錠する（PG 18 実測）。
   *
   * <p>投影で返すのは {@link #findIdentityByMemberCode} と同じ理由 — 実体を先に載せると後段の {@link #findByIdForUpdate}
   * がロックの昇格になる。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m.id from com.kizuna.member.domain.Member m where m.id = :id")
  Optional<Long> lockIdForUpdate(@Param("id") Long id);

  // 職員の会員検索。退会済みの行も対象にするので識別子で単一結果を前提にしない（ADR 0029）。
  // 利用者は FQCN で参照する（HQL の予約語衝突を避ける既存規約）。検索語は呼出側が '!' で
  // エスケープ済みの LIKE パターンとして渡し、空検索は '%' で全件に当てる。
  String SEARCH_SELECT =
      """
      select m.id as id, m.memberCode as memberCode, m.rank as rank,
             m.withdrawnAt as withdrawnAt, m.createdAt as createdAt,
             u.email as email, u.displayName as displayName
      from com.kizuna.member.domain.Member m
        join com.kizuna.user.domain.User u on u.id = m.userId
      """;

  String SEARCH_COUNT =
      """
      select count(m) from com.kizuna.member.domain.Member m
        join com.kizuna.user.domain.User u on u.id = m.userId
      """;

  String SEARCH_MATCH =
      " (lower(m.memberCode) like :pattern escape '!'"
          + " or lower(u.email) like :pattern escape '!'"
          + " or lower(u.displayName) like :pattern escape '!')";

  // 登録の新しい順。createdAt の同値は id で決定的に解く。
  String SEARCH_ORDER = " order by m.createdAt desc, m.id desc";

  @Query(
      value = SEARCH_SELECT + " where m.withdrawnAt is null and" + SEARCH_MATCH + SEARCH_ORDER,
      countQuery = SEARCH_COUNT + " where m.withdrawnAt is null and" + SEARCH_MATCH)
  Page<MemberSearchView> searchActive(@Param("pattern") String pattern, Pageable pageable);

  @Query(
      value = SEARCH_SELECT + " where m.withdrawnAt is not null and" + SEARCH_MATCH + SEARCH_ORDER,
      countQuery = SEARCH_COUNT + " where m.withdrawnAt is not null and" + SEARCH_MATCH)
  Page<MemberSearchView> searchWithdrawn(@Param("pattern") String pattern, Pageable pageable);

  @Query(
      value = SEARCH_SELECT + " where" + SEARCH_MATCH + SEARCH_ORDER,
      countQuery = SEARCH_COUNT + " where" + SEARCH_MATCH)
  Page<MemberSearchView> searchAll(@Param("pattern") String pattern, Pageable pageable);
}
