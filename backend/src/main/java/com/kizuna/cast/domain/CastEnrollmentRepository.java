package com.kizuna.cast.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CastEnrollmentRepository extends JpaRepository<CastEnrollment, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from CastEnrollment e where e.id = :id")
  Optional<CastEnrollment> findScopedByIdForUpdate(@Param("id") String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from CastEnrollment e where e.id = :id")
  Optional<CastEnrollment> findByIdForUpdate(@Param("id") String id);

  @Query("select e.castId from CastEnrollment e where e.id = :id")
  Optional<Long> findCastIdById(@Param("id") String id);

  @Query(
      "select e.id from CastEnrollment e join com.kizuna.cast.domain.Cast c on c.id = e.castId where c.platformUserId = :userId")
  List<String> findIdsByPlatformUserId(@Param("userId") Long userId);

  @Query(
      "select e.id from CastEnrollment e join com.kizuna.cast.domain.Cast c on c.id = e.castId where c.platformUserId = :userId and e.storeId = :storeId order by e.id")
  List<String> findIdsByPlatformUserIdAndStoreId(
      @Param("userId") Long userId, @Param("storeId") Long storeId);

  @Query(
      "select distinct e.storeId as storeId, st.name as storeName from CastEnrollment e join com.kizuna.cast.domain.Cast c on c.id = e.castId join com.kizuna.store.domain.Store st on st.id = e.storeId where c.platformUserId = :userId order by st.name")
  List<CastStoreView> findStoresByPlatformUserId(@Param("userId") Long userId);
}
