package com.kizuna.review.publication;

import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewRepository;
import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.Status;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewPublication {
  private final ReviewRepository reviews;
  private final StoreContext context;

  @StoreScoped
  @Transactional(readOnly = true)
  public Map<String, ApprovedReviewProjection> current(Long storeId, Set<String> reviewIds) {
    if (storeId == null || !storeId.equals(context.getStoreId()))
      throw new AccessDeniedException("口コミの店舗指定を確認してください");
    if (reviewIds == null || reviewIds.size() > 100)
      throw new IllegalArgumentException("口コミの取得件数は100件以内で指定してください");
    reviewIds.forEach(ReviewInput::id);
    var result = new LinkedHashMap<String, ApprovedReviewProjection>();
    for (var r :
        reviews.findByIdInAndStatusAndPermissionStatus(
            reviewIds, Status.APPROVED, PermissionStatus.GRANTED)) {
      result.put(
          r.getId(),
          new ApprovedReviewProjection(r.getId(), r.getVersion(), r.getDisplayName(), r.getBody()));
    }
    return Map.copyOf(result);
  }
}
