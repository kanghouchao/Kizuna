package com.kizuna.shift.dashboard;

import com.kizuna.shift.domain.ShiftRequestStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ダッシュボードの区画が数える出勤希望の読み取り。権限は区画の端点が守り、ここは出勤管理の権限を求めない。 */
@Service
@RequiredArgsConstructor
public class ShiftDashboardReader {
  private final EntityManager em;

  /** PENDING の出勤希望すべて。目標営業日を過ぎても却下はできるので、過ぎたものも処理すべき件数に入れる。 */
  @Transactional(readOnly = true)
  public long pendingRequests(Long storeId) {
    return em.createQuery(
            """
            select count(r) from com.kizuna.shift.domain.ShiftRequest r
            where r.storeId = :store and r.status = :pending
            """,
            Long.class)
        .setParameter("store", storeId)
        .setParameter("pending", ShiftRequestStatus.PENDING)
        .getSingleResult();
  }
}
