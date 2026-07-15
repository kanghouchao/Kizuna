package com.kizuna.cast.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CastRepository
    extends JpaRepository<Cast, String>, JpaSpecificationExecutor<Cast> {
  Page<Cast> findByNameContainingIgnoreCase(String name, Pageable pageable);

  List<Cast> findByStatusOrderByDisplayOrderAsc(String status);

  /**
   * 档案に紐づく平台身分 ID を取得する（未紐づけなら空）。
   *
   * <p>スカラ投影のため一次キャッシュ上の管理エンティティを経由せず、常に DB の最新コミット値を読む。
   * 再発行の直前に、初回チェック以降で並行受諾が档案を紐づけていないかを再確認するために使う。
   */
  @Query("select c.platformUserId from Cast c where c.id = :id")
  Optional<Long> findPlatformUserIdById(@Param("id") String id);
}
