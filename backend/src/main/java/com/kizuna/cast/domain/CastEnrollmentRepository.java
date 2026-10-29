package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CastEnrollmentRepository extends StoreScopedRepository<CastEnrollment> {
  /** 店舗を跨ぐ本人照会。境界は本人 id（cast_id）。 */
  @Query(
      """
      select e.id as id, e.storeId as storeId, st.name as storeName,
             p.name as name, e.status as status, e.startedOn as startedOn,
             e.endedAt as endedAt
      from CastEnrollment e
      join com.kizuna.store.domain.Store st on st.id = e.storeId
      join CastProfile p on p.enrollmentId = e.id and p.storeId = e.storeId
      where e.castId = :personId
      order by e.createdAt desc, e.id desc
      """)
  Page<CastPersonEnrollmentView> findPersonEnrollments(
      @Param("personId") Long personId, Pageable pageable);

  /** 店舗を跨ぐ本人照会。境界は在籍 id と本人 id（cast_id）の一致。 */
  boolean existsByIdAndCastId(String id, Long castId);

  List<CastEnrollment> findByIdInAndStoreId(Collection<String> ids, Long storeId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from CastEnrollment e where e.storeId = :storeId order by e.id")
  List<CastEnrollment> findAllByStoreIdForUpdate(@Param("storeId") Long storeId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from CastEnrollment e where e.id = :id and e.storeId = :storeId")
  Optional<CastEnrollment> findByIdAndStoreIdForUpdate(
      @Param("id") String id, @Param("storeId") Long storeId);

  @Query("select e.castId from CastEnrollment e where e.id = :id and e.storeId = :storeId")
  Optional<Long> findCastIdByIdAndStoreId(@Param("id") String id, @Param("storeId") Long storeId);

  /** 店舗を跨ぐ本人照会。境界は本人の user_id。 */
  @Query(
      "select e.id from CastEnrollment e join com.kizuna.cast.domain.Cast c on c.id = e.castId where c.userId = :userId")
  List<String> findIdsByUserId(@Param("userId") Long userId);

  @Query(
      "select e.id from CastEnrollment e join com.kizuna.cast.domain.Cast c on c.id = e.castId where c.userId = :userId and e.storeId = :storeId and e.status <> com.kizuna.cast.domain.CastEnrollmentStatus.WITHDRAWN order by e.id")
  List<String> findIdsByUserIdAndStoreId(
      @Param("userId") Long userId, @Param("storeId") Long storeId);

  /** 店舗を跨ぐ本人照会。境界は本人の user_id。 */
  @Query(
      "select distinct e.storeId as storeId, st.name as storeName from CastEnrollment e join com.kizuna.cast.domain.Cast c on c.id = e.castId join com.kizuna.store.domain.Store st on st.id = e.storeId where c.userId = :userId and e.status <> com.kizuna.cast.domain.CastEnrollmentStatus.WITHDRAWN order by st.name")
  List<CastStoreView> findStoresByUserId(@Param("userId") Long userId);

  @Query(
      """
      select new com.kizuna.cast.domain.CastCascadeCounts(
        (select count(h) from CastEnrollmentStatusHistory h
          where h.storeId = :storeId and h.enrollmentId = :id),
        (select count(s) from CastEnrollmentSnapshot s
          where s.storeId = :storeId and s.enrollmentId = :id),
        (select count(i) from CastInvitation i
          where i.storeId = :storeId and i.castId = :id))
      from CastEnrollment e where e.id = :id and e.storeId = :storeId
      """)
  CastCascadeCounts countCascade(@Param("id") String id, @Param("storeId") Long storeId);
}
