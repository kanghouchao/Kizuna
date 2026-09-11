package com.kizuna.cast.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CastRepository extends JpaRepository<Cast, Long> {
  @Query(
      """
      select c.id as id, u.displayName as displayName, c.realName as realName
      from Cast c join com.kizuna.user.domain.PlatformUser u on u.id = c.platformUserId
      where lower(u.displayName) like :pattern escape '\\'
         or lower(c.realName) like :pattern escape '\\'
      order by c.id asc
      """)
  Page<CastPersonSummaryView> searchPeople(@Param("pattern") String pattern, Pageable pageable);

  @Query(
      """
      select c.id as id, c.platformUserId as platformUserId, u.displayName as displayName,
             c.realName as realName, c.birthDate as birthDate
      from Cast c join com.kizuna.user.domain.PlatformUser u on u.id = c.platformUserId
      where c.id = :id
      """)
  Optional<CastPersonView> findPerson(@Param("id") Long id);

  Optional<Cast> findByPlatformUserId(Long platformUserId);
}
