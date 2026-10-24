package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CastProfileRepository extends StoreScopedRepository<CastProfile> {
  Optional<CastProfile> findByEnrollmentIdAndStoreId(String enrollmentId, Long storeId);

  List<CastProfile> findByStoreId(Long storeId);

  @Query(
      "select new com.kizuna.cast.domain.CastManagementView(e, p) from CastEnrollment e join CastProfile p on p.enrollmentId = e.id and p.storeId = e.storeId where e.storeId = :storeId and lower(p.name) like lower(:pattern) escape '\\'")
  Page<CastManagementView> search(
      @Param("storeId") Long storeId, @Param("pattern") String pattern, Pageable pageable);

  @Query(
      "select p from CastProfile p join CastEnrollment e on e.id = p.enrollmentId and e.storeId = p.storeId where p.storeId = :storeId and p.publicationStatus = com.kizuna.cast.domain.CastPublicationStatus.PUBLISHED and e.status = com.kizuna.cast.domain.CastEnrollmentStatus.ENROLLED order by p.displayOrder")
  List<CastProfile> findPublished(@Param("storeId") Long storeId);

  @Query(
      "select p from CastProfile p join CastEnrollment e on e.id = p.enrollmentId and e.storeId = p.storeId where p.storeId = :storeId and e.status = com.kizuna.cast.domain.CastEnrollmentStatus.ENROLLED and lower(p.name) like lower(:pattern) escape '\\' order by p.displayOrder, p.enrollmentId")
  List<CastProfile> findCandidates(
      @Param("storeId") Long storeId, @Param("pattern") String pattern, Limit limit);
}
