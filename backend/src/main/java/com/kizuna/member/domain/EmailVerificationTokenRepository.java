package com.kizuna.member.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, Long> {

  /** ダイジェストで引いて行を押さえる。並行する同じトークンの確認は、後着がロック後に消費済みを見る。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select t from com.kizuna.member.domain.EmailVerificationToken t"
          + " where t.tokenDigest = :tokenDigest")
  Optional<EmailVerificationToken> findByTokenDigestForUpdate(String tokenDigest);
}
