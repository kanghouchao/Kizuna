package com.kizuna.shared.persistence;

import java.util.Optional;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/** 店舗級エンティティの repository 基盤。店舗条件を持たない取得口（findById / findAll 等）は公開しない。 */
@NoRepositoryBean
public interface StoreScopedRepository<T extends StoreScopedEntity> extends Repository<T, String> {

  <S extends T> S save(S entity);

  <S extends T> S saveAndFlush(S entity);

  void flush();

  void delete(T entity);

  Optional<T> findByIdAndStoreId(String id, Long storeId);
}
