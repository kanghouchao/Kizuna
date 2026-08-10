package com.kizuna.customer.application;

import com.kizuna.customer.api.dto.CustomerPointAdjustmentRequest;
import com.kizuna.customer.api.dto.CustomerPointBalanceResponse;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.user.domain.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 店舗の CRM から見た会員ポイントのユースケース。残高は顧客ではなく紐づく会員の台帳が持つため、店舗側は顧客 ID を入口にして 台帳へ問い合わせる。
 *
 * <p>顧客の指名は id で行うため、他店舗の顧客は storeFilter 経由で 404 となり存在の有無が漏れない。
 *
 * <p>増減 0・減算への有効期限・残高不足はいずれも台帳側の判定に委ねる。入口ごとに規則を書き写すと、規則が変わったときに片方だけ古くなる。
 */
@Service
@RequiredArgsConstructor
public class CustomerPointService {

  private final CustomerRepository customerRepository;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final PointLedgerService pointLedgerService;
  private final PlatformUserRepository platformUserRepository;
  private final StoreContext storeContext;

  @StoreScoped
  @Transactional(readOnly = true)
  public CustomerPointBalanceResponse balance(String customerId) {
    requireCustomer(customerId);
    Long memberId = activeMemberId(customerId);
    if (memberId == null) {
      return CustomerPointBalanceResponse.builder().linked(false).build();
    }
    return linkedBalance(memberId);
  }

  /**
   * 運用者による手動調整。発生店舗は操作中の店舗文脈で、残高の作用域ではなく帰属情報として台帳へ残る。
   *
   * <p>会員に紐づかない顧客は調整できない。台帳は会員が持つものなので、紐づけの無い顧客には積み先そのものが無い。
   */
  @StoreScoped
  @Transactional
  public CustomerPointBalanceResponse adjust(
      String customerId, CustomerPointAdjustmentRequest request, String actorEmail) {
    requireCustomer(customerId);
    Long memberId = activeMemberId(customerId);
    if (memberId == null) {
      throw new ServiceException("会員に紐づいていない顧客のポイントは調整できません");
    }

    Long actorId = resolveActorId(actorEmail);
    pointLedgerService.adjust(
        memberId,
        storeContext.getStoreId(),
        request.getDelta(),
        request.getReason(),
        request.getExpiresOn(),
        actorId);
    return linkedBalance(memberId);
  }

  private CustomerPointBalanceResponse linkedBalance(long memberId) {
    return CustomerPointBalanceResponse.builder()
        .linked(true)
        .balance(pointLedgerService.balance(memberId))
        .build();
  }

  private void requireCustomer(String customerId) {
    if (!customerRepository.existsById(customerId)) {
      throw new NotFoundException("顧客が見つかりません: " + customerId);
    }
  }

  /**
   * JWT は user-id claim を持たないため、実行者は認証主体の email から解決する。
   *
   * <p>解決できない認証主体は黙って null にせず失敗させる — 追記型の台帳では実行者 null が「機構が起こした仕訳」の形であり、
   * 失効した認証セッションによる人手の操作がそれと区別できなくなる。
   */
  private Long resolveActorId(String actorEmail) {
    return platformUserRepository
        .findByEmail(actorEmail)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }

  /**
   * 顧客に紐づく会員。紐づけが無い場合と、会員行が消えて紐づけの会員 ID が欠落した場合はいずれも null。
   *
   * <p>会員 ID の欠落を紐づけの不在と同じに扱うのは、残高の所在が会員 ID でしか辿れないため。
   */
  private Long activeMemberId(String customerId) {
    return customerMemberLinkRepository
        .findByCustomerIdAndStatus(customerId, LinkStatus.ACTIVE)
        .map(CustomerMemberLink::getMemberId)
        .orElse(null);
  }
}
