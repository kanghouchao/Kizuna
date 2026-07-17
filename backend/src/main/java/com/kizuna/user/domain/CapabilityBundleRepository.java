package com.kizuna.user.domain;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CapabilityBundleRepository extends JpaRepository<CapabilityBundle, Long> {

  Optional<CapabilityBundle> findByName(String name);

  /**
   * 指定した束集合のいずれかが能力を含むか。呼び出し側は bundleIds の非空を保証すること（STAFF は「1 束以上」の不変条件があるため、STAFF の束集合をそのまま渡せる）。
   */
  @Query(
      "select count(b) > 0 from com.kizuna.user.domain.CapabilityBundle b"
          + " join b.capabilities c where b.id in :bundleIds and c = :capability")
  boolean anyBundleHasCapability(
      @Param("bundleIds") Set<Long> bundleIds, @Param("capability") Capability capability);
}
