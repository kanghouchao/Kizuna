package com.kizuna.customer.application;

import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.user.application.BusinessAudit;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員退会に伴う関連の解除。全店舗の ACTIVE 関連を同じ理由で解除し、顧客行は残す（GLOSSARY「会員退会」）。
 *
 * <p>顧客行のロックは取らない。退会は会員行を先に押さえており、関連の成立は会員行へ KEY SHARE を置く挿入なので、ここで顧客行の FOR UPDATE
 * まで求めると紐づけとの間で死錠する。並行する解除との競合は行の版が 409 にする。
 */
@Service
@RequiredArgsConstructor
@NamedInterface("application")
@Transactional(propagation = Propagation.MANDATORY)
public class CustomerMemberLinkWithdrawal {

  public static final String REASON = "会員退会";

  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final BusinessAudit audit;

  /** 会員の全 ACTIVE 関連を解除し、解除した件数を返す。 */
  public int releaseAll(long memberId, long actorId, OffsetDateTime releasedAt) {
    List<CustomerMemberLink> links =
        customerMemberLinkRepository.findByMemberIdAndStatusOrderByIdAsc(
            memberId, LinkStatus.ACTIVE);
    for (CustomerMemberLink link : links) {
      var before = CustomerMemberLinkAudit.snapshot(link);
      link.release(actorId, REASON, releasedAt);
      customerMemberLinkRepository.save(link);
      audit.recordById(
          actorId,
          link.getStoreId(),
          CustomerMemberLinkAudit.RELEASED,
          "CUSTOMER_MEMBER_LINK",
          link.getId(),
          "CUSTOMER",
          link.getCustomerId(),
          before,
          CustomerMemberLinkAudit.snapshot(link));
    }
    customerMemberLinkRepository.flush();
    return links.size();
  }
}
