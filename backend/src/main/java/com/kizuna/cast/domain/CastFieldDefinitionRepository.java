package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CastFieldDefinitionRepository extends StoreScopedRepository<CastFieldDefinition> {

  boolean existsByStoreIdAndKey(Long storeId, String key);

  long countByStoreId(Long storeId);

  List<CastFieldDefinition> findAllByStoreIdOrderByDisplayOrderAsc(Long storeId);

  List<CastFieldDefinition> findByStoreIdAndIsPublicTrueOrderByDisplayOrderAsc(Long storeId);

  @Query("select max(d.displayOrder) from CastFieldDefinition d where d.storeId = :storeId")
  Integer findMaxDisplayOrder(@Param("storeId") Long storeId);
}
