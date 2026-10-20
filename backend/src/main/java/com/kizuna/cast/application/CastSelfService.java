package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastStoreResponse;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.user.domain.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本人（キャスト）ポータルの所属店舗解決ユースケース。
 *
 * <p>cast_id 単層自限（{@link CastEnrollmentRepository#findIdsByUserId}）と同じ基点（user_id 逆引き）で、StoreContext
 * を経由せず跨店で解決する。
 */
@Service
@RequiredArgsConstructor
public class CastSelfService {

  private final UserRepository userRepository;
  private final CastEnrollmentRepository castRepository;

  @StoreScopeExempt(reason = "認証済み email から逆引きした本人の user_id が唯一の境界で、所属店舗の跨店解決自体が業務要件")
  @Transactional(readOnly = true)
  public List<CastStoreResponse> myStores(String email) {
    Long userId =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
            .getId();
    return castRepository.findStoresByUserId(userId).stream()
        .map(view -> new CastStoreResponse(view.getStoreId(), view.getStoreName()))
        .toList();
  }
}
