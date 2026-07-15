package com.kizuna.cast.domain;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CastInvitationRepository extends JpaRepository<CastInvitation, String> {

  Optional<CastInvitation> findByToken(String token);

  List<CastInvitation> findByCastIdAndStatus(String castId, CastInvitation.Status status);

  List<CastInvitation> findByCastIdIn(Collection<String> castIds);

  /**
   * PENDING の招待のみを条件付きで ACCEPTED へ遷移させ（受諾のクレーム）、更新行数を返す。
   *
   * <p>同一招待への並行受諾は DB の行ロックで直列化され、先着のみが 1 行を更新できる。後着は既に ACCEPTED となった行を {@code status = PENDING}
   * 条件に一致させられず 0 行となるため、呼び出し側は更新行数で受諾権を 判定できる（身分作成・紐づけの前に受諾を確定する直列化の根拠）。
   */
  @Modifying
  @Query(
      "update CastInvitation i set i.status = :accepted, i.acceptedAt = :now "
          + "where i.id = :id and i.status = :pending")
  int claimPending(
      @Param("id") String id,
      @Param("now") OffsetDateTime now,
      @Param("pending") CastInvitation.Status pending,
      @Param("accepted") CastInvitation.Status accepted);
}
