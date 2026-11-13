package com.kizuna.review.dashboard;

import com.kizuna.review.domain.ReviewValues.Status;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ダッシュボードの区画が数える口コミの読み取り。権限は区画の端点が守り、ここは口コミの閲覧の権限を求めない。 */
@Service
@RequiredArgsConstructor
public class ReviewDashboardReader {
  private final EntityManager em;

  /** 審査待ち（PENDING）の口コミ。訂正で置き換えられた行は取り下げに移るので、PENDING だけで現行の行に絞れる。 */
  @Transactional(readOnly = true)
  public long pendingReviews(Long storeId) {
    return em.createQuery(
            """
            select count(r) from com.kizuna.review.domain.ReviewRecord r
            where r.storeId = :store and r.status = :pending
            """,
            Long.class)
        .setParameter("store", storeId)
        .setParameter("pending", Status.PENDING)
        .getSingleResult();
  }
}
