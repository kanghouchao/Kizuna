package com.kizuna.user.domain;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CapabilityBundleRepository extends JpaRepository<CapabilityBundle, Long> {

  Optional<CapabilityBundle> findByName(String name);

  /** 指定した能力を含む束の id 集合。呼び出し側はユーザーの束集合とこの集合の共通部分の有無で能力保持を判定する。 */
  @Query(
      "select b.id from com.kizuna.user.domain.CapabilityBundle b"
          + " join b.capabilities c where c = :capability")
  Set<Long> findIdsByCapability(@Param("capability") Capability capability);
}
