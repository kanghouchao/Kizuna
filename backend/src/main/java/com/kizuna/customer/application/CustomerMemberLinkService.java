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
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.UserRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 来店した会員を店舗の顧客台帳へ紐づけるユースケース。紐づけ・変更・解除はいずれも履歴として残り、行は削除しない。
 *
 * <p>紐づけは会員コードの読み取りによる明示操作のみで成立する（電話番号等による自動マッチングは行わない既定）。顧客は店舗 id と id で引くため、他店舗の顧客は 404
 * となり存在の有無が漏れない。
 */
@Service
@RequiredArgsConstructor
public class CustomerMemberLinkService {

  /** 文面と、4 経路で揃えている理由は {@code CustomerService} の同名の定数に記す。 */
  private static final String MERGED_CUSTOMER_NOT_EDITABLE = "統合済みの顧客です。統合先の顧客を編集してください";

  private final CustomerRepository customerRepository;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final MemberLookupService memberLookupService;
  private final UserRepository userRepository;
  private final BusinessAudit audit;

  @Transactional
  public CustomerMemberLinkResponse link(
      Long storeId,
      String customerId,
      String memberCode,
      String expectedLinkId,
      String operationReason,
      String actorEmail) {
    Long actorId = resolveActorId(actorEmail);
    lockEditableCustomer(storeId, customerId);
    String targetId = customerId;
    CustomerMemberLink current =
        customerMemberLinkRepository
            .findByStoreIdAndCustomerIdAndStatus(storeId, targetId, LinkStatus.ACTIVE)
            .orElse(null);
    requireExpectedLink(current, expectedLinkId);
    MemberLookup member =
        memberLookupService
            .findByMemberCode(memberCode)
            .orElseThrow(() -> new NotFoundException("会員コードに該当する会員が見つかりません"));
    // 退会済み会員へは紐づけない（残高零・帰属物化なしの不変量 — ADR 0030）。判定は会員行を押さえてから —
    // 押さえないと、並行する退会が全関連を解除した後にこの挿入が確定し、退会済み会員に ACTIVE 関連が残る。
    memberLookupService.lockForBalanceConfirmation(member.memberId());
    if (memberLookupService.isWithdrawn(member.memberId())) {
      throw new ConflictException("退会済みの会員は紐づけできません");
    }
    String normalizedReason =
        CustomerMemberLink.normalizeOperationReason(operationReason, current != null);
    OffsetDateTime operatedAt = OffsetDateTime.now();
    if (current != null && member.memberId().equals(current.getMemberId())) {
      throw new ConflictException("この顧客は既にこの会員と紐づいています");
    }
    if (customerMemberLinkRepository.existsByStoreIdAndMemberIdAndStatus(
        storeId, member.memberId(), LinkStatus.ACTIVE)) {
      throw new ConflictException("この会員は既に他の顧客と紐づいています");
    }

    if (current != null) {
      // 紐づけ先の変更は解除と新規紐づけを同一トランザクションで行い、どちらでもない中間状態を外へ見せない。
      // 部分一意索引（customer_id WHERE status='ACTIVE'）は据置不可なので、新しい行の INSERT より先に
      // 旧行の UPDATE を DB へ流す — flush の既定順は INSERT が先で、そのままでは自分自身と衝突する。
      var before = snapshot(current);
      current.release(actorId, normalizedReason, operatedAt);
      customerMemberLinkRepository.saveAndFlush(current);
      audit.recordById(
          actorId,
          current.getStoreId(),
          "CUSTOMER_MEMBER_LINK_RELEASED",
          "CUSTOMER_MEMBER_LINK",
          current.getId(),
          "CUSTOMER",
          current.getCustomerId(),
          before,
          snapshot(current));
    }

    CustomerMemberLink link =
        CustomerMemberLink.builder()
            .customerId(targetId)
            .memberId(member.memberId())
            .memberCode(member.memberCode())
            // この経路の成立根拠は会員コードの提示ただ一つ。他の根拠はそれぞれの機構の書き手が記録する。
            .reason(LinkReason.MEMBER_CODE)
            .operationReason(normalizedReason)
            .linkedBy(actorId)
            .linkedAt(operatedAt)
            .build();
    link.setStoreId(storeId);
    // 事前チェックをすり抜けた並行紐づけが部分一意索引に当たるレースはここで catch しない —
    // CommonExceptionHandler が SQLSTATE で一意違反だけを 409 へ写像し、FK 等の他の整合性違反は
    // 実装欠陥として 500 のまま大きく失敗させる分類を持っているため、そこへ委ねる。
    CustomerMemberLink saved = customerMemberLinkRepository.saveAndFlush(link);
    audit.recordById(
        actorId,
        saved.getStoreId(),
        "CUSTOMER_MEMBER_LINK_CREATED",
        "CUSTOMER_MEMBER_LINK",
        saved.getId(),
        "CUSTOMER",
        saved.getCustomerId(),
        Map.of(),
        snapshot(saved));
    return new CustomerMemberLinkResponse(
        saved.getId(), true, saved.getMemberCode(), saved.getLinkedAt());
  }

  @Transactional
  public void unlink(
      Long storeId,
      String customerId,
      String expectedLinkId,
      String operationReason,
      String actorEmail) {
    Long actorId = resolveActorId(actorEmail);
    lockEditableCustomer(storeId, customerId);
    CustomerMemberLink current =
        customerMemberLinkRepository
            .findByStoreIdAndCustomerIdAndStatus(storeId, customerId, LinkStatus.ACTIVE)
            .orElseThrow(() -> new ConflictException("関連状態が変わりました。再取得して確認してください"));
    requireExpectedLink(current, expectedLinkId);
    var before = snapshot(current);
    current.release(actorId, operationReason, OffsetDateTime.now());
    customerMemberLinkRepository.save(current);
    customerMemberLinkRepository.flush();
    audit.recordById(
        actorId,
        current.getStoreId(),
        "CUSTOMER_MEMBER_LINK_RELEASED",
        "CUSTOMER_MEMBER_LINK",
        current.getId(),
        "CUSTOMER",
        current.getCustomerId(),
        before,
        snapshot(current));
  }

  private static Map<String, String> snapshot(CustomerMemberLink link) {
    return CustomerMemberLinkAudit.snapshot(link);
  }

  /**
   * 顧客に現に有効な紐づけ。無ければ 404 — 「紐づいていない」を 200 で表すと、呼出側が本体を読んでから もう一度分岐することになり、操作の可否を状態の有無だけで決められなくなる。
   */
  @Transactional(readOnly = true)
  public CustomerMemberLinkResponse current(Long storeId, String customerId) {
    requireCustomer(storeId, customerId);
    return customerMemberLinkRepository
        .findByStoreIdAndCustomerIdAndStatus(storeId, customerId, LinkStatus.ACTIVE)
        .map(
            link ->
                new CustomerMemberLinkResponse(
                    link.getId(), true, link.getMemberCode(), link.getLinkedAt()))
        .orElseThrow(() -> new NotFoundException("紐づけられている会員がいません"));
  }

  /** 顧客 1 件の紐づけ履歴。履歴は解除・再紐づけのたびに増え続けるので、ページで区切って返す。 */
  @Transactional(readOnly = true)
  public Page<CustomerMemberLinkHistoryResponse> history(
      Long storeId, String customerId, Pageable pageable) {
    requireCustomer(storeId, customerId);
    return customerMemberLinkRepository
        .findHistory(storeId, customerId, pageable)
        .map(CustomerMemberLinkService::toHistoryResponse);
  }

  private void requireCustomer(Long storeId, String customerId) {
    if (!customerRepository.existsByIdAndStoreId(customerId, storeId)) {
      throw new NotFoundException("顧客が見つかりません");
    }
  }

  /** 成立・変更・解除は名指された顧客行を直列化点とする。他店舗は 404、統合済みは 409 とし、 操作対象を暗黙に存続顧客へ向け直さない。 */
  private void lockEditableCustomer(Long storeId, String customerId) {
    customerRepository
        .findByIdAndStoreIdForUpdate(customerId, storeId)
        .orElseThrow(() -> new NotFoundException("顧客が見つかりません"));
    if (customerRepository.isMerged(storeId, customerId)) {
      throw new ConflictException(MERGED_CUSTOMER_NOT_EDITABLE);
    }
  }

  private static void requireExpectedLink(CustomerMemberLink current, String expectedLinkId) {
    if (!Objects.equals(current == null ? null : current.getId(), expectedLinkId)) {
      throw new ConflictException("関連状態が変わりました。再取得して確認してください");
    }
  }

  /** JWT は user-id claim を持たないため、実行者は認証主体の email から解決する。 */
  private Long resolveActorId(String actorEmail) {
    return userRepository
        .findByEmail(actorEmail)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }

  private static CustomerMemberLinkHistoryResponse toHistoryResponse(CustomerMemberLinkView view) {
    return new CustomerMemberLinkHistoryResponse(
        view.getId(),
        view.getMemberCode(),
        view.getStatus(),
        view.getReason(),
        view.getOperationReason(),
        view.getReleaseReason(),
        view.getLinkedBy(),
        view.getReleasedBy(),
        view.getLinkedAt(),
        view.getLinkedByName(),
        view.getReleasedAt(),
        view.getReleasedByName());
  }
}
