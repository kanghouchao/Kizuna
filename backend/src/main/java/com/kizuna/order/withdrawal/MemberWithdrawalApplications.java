package com.kizuna.order.withdrawal;

import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.shared.exception.ConflictException;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員退会に伴う予約申請の整理。会員名義の PENDING をすべて WITHDRAWN にし、CONFIRMED 以降には触れない。
 *
 * <p>申請行は待たずに押さえる。確定処理が押さえている申請があれば、退会は競合（409）として丸ごと巻き戻し、利用者がやり直す —
 * 退会側は会員行を先に握っているので、申請行を待つと確定処理の関連挿入（会員行への KEY SHARE）と死錠する。
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MemberWithdrawalApplications {

  private final OrderApplicationRepository applications;

  /** 取り下げた申請の件数を返す。 */
  public int withdrawPending(long memberId, long actorId, OffsetDateTime at) {
    List<OrderApplication> pending;
    try {
      pending = applications.lockPendingByRequesterMemberIdWithoutWaiting(memberId);
    } catch (PessimisticLockingFailureException ex) {
      throw new ConflictException("予約申請の確定処理と競合しました。しばらくしてからやり直してください");
    }
    for (OrderApplication application : pending) {
      application.withdraw(actorId, at);
      applications.save(application);
    }
    applications.flush();
    return pending.size();
  }
}
