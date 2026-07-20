package com.kizuna.cast.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** カスタムフィールド定義のリポジトリ。storeFilter 前提のため店舗条件は書かない（CastRepository と同流儀）。 */
public interface CastFieldDefinitionRepository extends JpaRepository<CastFieldDefinition, String> {

  boolean existsByKey(String key);

  List<CastFieldDefinition> findAllByOrderByDisplayOrderAsc();

  List<CastFieldDefinition> findByIsPublicTrueOrderByDisplayOrderAsc();

  @Query("select max(d.displayOrder) from CastFieldDefinition d")
  Integer findMaxDisplayOrder();
}
