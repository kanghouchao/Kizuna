package com.kizuna.cast.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CastProfileRepository extends JpaRepository<CastProfile, String> {
  Optional<CastProfile> findByEnrollmentId(String enrollmentId);

  @Query(
      "select new com.kizuna.cast.domain.CastManagementView(e, p) from CastEnrollment e join CastProfile p on p.enrollmentId = e.id where lower(p.name) like lower(:pattern) escape '\\'")
  Page<CastManagementView> search(@Param("pattern") String pattern, Pageable pageable);

  @Query(
      "select p from CastProfile p join CastEnrollment e on e.id = p.enrollmentId where p.publicationStatus = com.kizuna.cast.domain.CastPublicationStatus.PUBLISHED and e.status = com.kizuna.cast.domain.CastEnrollmentStatus.ENROLLED order by p.displayOrder")
  List<CastProfile> findPublished();

  @Query(
      "select p from CastProfile p join CastEnrollment e on e.id = p.enrollmentId where e.storeId = :storeId and e.status = com.kizuna.cast.domain.CastEnrollmentStatus.ENROLLED and lower(p.name) like lower(:pattern) escape '\\' order by p.displayOrder, p.enrollmentId")
  List<CastProfile> findCandidates(
      @Param("storeId") Long storeId, @Param("pattern") String pattern, Limit limit);
}
