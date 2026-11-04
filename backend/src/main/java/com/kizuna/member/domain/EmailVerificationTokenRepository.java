package com.kizuna.member.domain;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** 店舗を持たない利用者の属性で、照合はトークンのダイジェスト、再発行と回数は利用者 ID を境界にする。 */
public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, Long> {

  /** ダイジェストで引いて行を押さえる。並行する同じトークンの確認は、後着がロック後に消費済みを見る。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select t from com.kizuna.member.domain.EmailVerificationToken t"
          + " where t.tokenDigest = :tokenDigest")
  Optional<EmailVerificationToken> findByTokenDigestForUpdate(String tokenDigest);

  // 一括 UPDATE で即時に失効させる。行を読んで書き換えると flush が INSERT を先に出し、新しい行が生きた行の一意索引に当たる。
  @Modifying
  @Query(
      "update EmailVerificationToken t set t.revokedAt = :at"
          + " where t.userId = :userId and t.consumedAt is null and t.revokedAt is null")
  int revokeLive(Long userId, OffsetDateTime at);

  Optional<EmailVerificationToken> findFirstByUserIdOrderByIssuedAtDesc(Long userId);

  long countByUserIdAndIssuedAtAfter(Long userId, OffsetDateTime since);
}
