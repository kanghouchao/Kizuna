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

  /**
   * 指定 platform_user_id に紐づく cast 行 id を跨店で返す（本人自限の基点）。
   *
   * <p>storeFilter を経由しない集約クエリのため常に全店横断で解決する。呼び出し側は認証済み本人の platform_user_id
   * のみを渡すため、リクエストパラメータ経由での注入点はない。
   */
  @Query("select c.id from Cast c where c.platformUserId = :platformUserId")
  List<String> findIdsByPlatformUserId(@Param("platformUserId") Long platformUserId);

  /**
   * 指定 platform_user_id が指定店舗に持つ本人 cast 行 id を古い順で返す（出勤希望提出の所属判定の基点）。
   *
   * <p>同一店舗に本人の档案が複数並存し得る（既存アカウントが同店の招待を複数受諾できる）ため、 単一結果を仮定せずリストで返し、呼び出し側が先頭（最古の档案）を決定的に選ぶ。
   *
   * <p>cast.status（ACTIVE 等）は見ない — 停止統制はアカウント層の責務であり、cast.status を下游の可否判定に使わない。 storeFilter
   * を経由しない集約クエリのため、呼び出し側は認証済み本人の platform_user_id のみを渡す。
   */
  @Query(
      """
      select c.id from Cast c
      where c.platformUserId = :platformUserId and c.storeId = :storeId
      order by c.id asc
      """)
  List<String> findIdsByPlatformUserIdAndStoreId(
      @Param("platformUserId") Long platformUserId, @Param("storeId") Long storeId);

  /**
   * 指定 platform_user_id の所属店舗一覧（id・店名）を跨店で返す（出勤希望提出フォームの店舗セレクタ用）。
   *
   * <p>同一店舗に本人の档案が複数並存し得るため distinct で店舗単位に畳む。 storeFilter を経由しない集約クエリのため常に全店横断で解決する。
   */
  @Query(
      """
      select distinct c.storeId as storeId, st.name as storeName
      from Cast c join com.kizuna.store.domain.Store st on st.id = c.storeId
      where c.platformUserId = :platformUserId
      order by st.name asc
      """)
  List<CastStoreView> findStoresByPlatformUserId(@Param("platformUserId") Long platformUserId);
}
