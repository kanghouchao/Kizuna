package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastStoreResponse;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本人（キャスト）ポータルの所属店舗解決ユースケース。
 *
 * <p>cast_id 単層自限（{@link CastRepository#findIdsByPlatformUserId}）と同じ基点（platform_user_id
 * 逆引き）で、StoreContext を経由せず跨店で解決する。
 */
@Service
@RequiredArgsConstructor
public class CastSelfService {

  private final PlatformUserRepository platformUserRepository;
  private final CastRepository castRepository;

  @StoreScopeExempt(reason = "認証済み email から逆引きした本人の platform_user_id が唯一の境界で、所属店舗の跨店解決自体が業務要件")
  @Transactional(readOnly = true)
  public List<CastStoreResponse> myStores(String email) {
    Long userId =
        platformUserRepository
            .findByEmail(email)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
            .getId();
    return castRepository.findStoresByPlatformUserId(userId).stream()
        .map(view -> new CastStoreResponse(view.getStoreId(), view.getStoreName()))
        .toList();
  }
}
