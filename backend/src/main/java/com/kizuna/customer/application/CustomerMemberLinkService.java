package com.kizuna.customer.application;

import com.kizuna.customer.api.dto.CustomerMemberLinkHistoryResponse;
import com.kizuna.customer.api.dto.CustomerMemberLinkResponse;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMemberLinkView;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 来店した会員を店舗の顧客台帳へ紐づけるユースケース。紐づけ・変更・解除はいずれも履歴として残り、行は削除しない。
 *
 * <p>紐づけは会員コードの読み取りによる明示操作のみで成立する（電話番号等による自動マッチングは行わない既定）。顧客の指名は id で行うため、 他店舗の顧客は storeFilter 経由で
 * 404 となり存在の有無が漏れない。
 */
@Service
@RequiredArgsConstructor
public class CustomerMemberLinkService {

  private final CustomerRepository customerRepository;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final MemberLookupService memberLookupService;
  private final PlatformUserRepository platformUserRepository;

  @StoreScoped
  @Transactional
  public CustomerMemberLinkResponse link(String customerId, String memberCode, String actorEmail) {
    Long actorId = resolveActorId(actorEmail);
    // 紐づけを読む前に顧客行を押さえ、記帳（受注完了・手動調整）と同じ直列化点に載せる。
    // 契約は CustomerRepository#findByIdForUpdate に記す。
    lockCustomer(customerId);
    MemberLookup member =
        memberLookupService
            .findByMemberCode(memberCode)
            .orElseThrow(() -> new NotFoundException("会員コードに該当する会員が見つかりません"));

    CustomerMemberLink current =
        customerMemberLinkRepository
            .findByCustomerIdAndStatus(customerId, LinkStatus.ACTIVE)
            .orElse(null);
    if (current != null && member.memberId().equals(current.getMemberId())) {
      throw new ConflictException("この顧客は既にこの会員と紐づいています");
    }
    if (customerMemberLinkRepository.existsByMemberIdAndStatus(
        member.memberId(), LinkStatus.ACTIVE)) {
      throw new ConflictException("この会員は既に他の顧客と紐づいています");
    }

    if (current != null) {
      // 紐づけ先の変更は解除と新規紐づけを同一トランザクションで行い、どちらでもない中間状態を外へ見せない。
      // 部分一意索引（customer_id WHERE status='ACTIVE'）は据置不可なので、新しい行の INSERT より先に
      // 旧行の UPDATE を DB へ流す — flush の既定順は INSERT が先で、そのままでは自分自身と衝突する。
      current.release(actorId);
      customerMemberLinkRepository.saveAndFlush(current);
    }

    CustomerMemberLink link =
        CustomerMemberLink.builder()
            .customerId(customerId)
            .memberId(member.memberId())
            .memberCode(member.memberCode())
            // この経路の成立根拠は会員コードの提示ただ一つ。他の根拠はそれぞれの機構の書き手が記録する。
            .reason(LinkReason.MEMBER_CODE)
            .linkedBy(actorId)
            .linkedAt(OffsetDateTime.now())
            .build();
    // store_id は StoreScopeStampListener が @PrePersist で採番する。
    // 事前チェックをすり抜けた並行紐づけが部分一意索引に当たるレースはここで catch しない —
    // CommonExceptionHandler が SQLSTATE で一意違反だけを 409 へ写像し、FK 等の他の整合性違反は
    // 実装欠陥として 500 のまま大きく失敗させる分類を持っているため、そこへ委ねる。
    CustomerMemberLink saved = customerMemberLinkRepository.saveAndFlush(link);
    return new CustomerMemberLinkResponse(true, saved.getMemberCode(), saved.getLinkedAt());
  }

  @StoreScoped
  @Transactional
  public void unlink(String customerId, String actorEmail) {
    Long actorId = resolveActorId(actorEmail);
    // 解除も紐づけと同じく顧客行を押さえてから現在の紐づけを読む。
    // 契約は CustomerRepository#findByIdForUpdate に記す。
    lockCustomer(customerId);
    CustomerMemberLink current =
        customerMemberLinkRepository
            .findByCustomerIdAndStatus(customerId, LinkStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("紐づけられている会員がいません"));
    current.release(actorId);
    customerMemberLinkRepository.save(current);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public List<CustomerMemberLinkHistoryResponse> history(String customerId) {
    requireCustomer(customerId);
    return customerMemberLinkRepository.findHistory(customerId).stream()
        .map(CustomerMemberLinkService::toHistoryResponse)
        .toList();
  }

  private void requireCustomer(String customerId) {
    if (!customerRepository.existsById(customerId)) {
      throw new NotFoundException("顧客が見つかりません");
    }
  }

  /** 紐づけを書き換える経路の直列化点。取得できない顧客は他店舗の顧客も含めて 404。 */
  private void lockCustomer(String customerId) {
    customerRepository
        .findByIdForUpdate(customerId)
        .orElseThrow(() -> new NotFoundException("顧客が見つかりません"));
  }

  /** JWT は user-id claim を持たないため、実行者は認証主体の email から解決する。 */
  private Long resolveActorId(String actorEmail) {
    return platformUserRepository
        .findByEmail(actorEmail)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }

  private static CustomerMemberLinkHistoryResponse toHistoryResponse(CustomerMemberLinkView view) {
    return new CustomerMemberLinkHistoryResponse(
        view.getId(),
        view.getMemberCode(),
        view.getStatus(),
        view.getLinkedAt(),
        view.getLinkedByName(),
        view.getReleasedAt(),
        view.getReleasedByName());
  }
}
