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
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
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

  /** 文面と、4 経路で揃えている理由は {@code CustomerService} の同名の定数に記す。 */
  private static final String MERGED_CUSTOMER_NOT_EDITABLE = "統合済みの顧客です。統合先の顧客を編集してください";

  private final CustomerRepository customerRepository;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final MemberLookupService memberLookupService;
  private final PlatformUserRepository platformUserRepository;

  @StoreScoped
  @Transactional
  public CustomerMemberLinkResponse link(
      String customerId,
      String memberCode,
      String expectedLinkId,
      String operationReason,
      String actorEmail) {
    Long actorId = resolveActorId(actorEmail);
    lockEditableCustomer(customerId);
    String targetId = customerId;
    CustomerMemberLink current =
        customerMemberLinkRepository
            .findByCustomerIdAndStatus(targetId, LinkStatus.ACTIVE)
            .orElse(null);
    requireExpectedLink(current, expectedLinkId);
    MemberLookup member =
        memberLookupService
            .findByMemberCode(memberCode)
            .orElseThrow(() -> new NotFoundException("会員コードに該当する会員が見つかりません"));
    String normalizedReason =
        CustomerMemberLink.normalizeOperationReason(operationReason, current != null);
    OffsetDateTime operatedAt = OffsetDateTime.now();
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
      current.release(actorId, normalizedReason, operatedAt);
      customerMemberLinkRepository.saveAndFlush(current);
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
    // store_id は StoreScopeStampListener が @PrePersist で採番する。
    // 事前チェックをすり抜けた並行紐づけが部分一意索引に当たるレースはここで catch しない —
    // CommonExceptionHandler が SQLSTATE で一意違反だけを 409 へ写像し、FK 等の他の整合性違反は
    // 実装欠陥として 500 のまま大きく失敗させる分類を持っているため、そこへ委ねる。
    CustomerMemberLink saved = customerMemberLinkRepository.saveAndFlush(link);
    return new CustomerMemberLinkResponse(
        saved.getId(), true, saved.getMemberCode(), saved.getLinkedAt());
  }

  @StoreScoped
  @Transactional
  public void unlink(
      String customerId, String expectedLinkId, String operationReason, String actorEmail) {
    Long actorId = resolveActorId(actorEmail);
    lockEditableCustomer(customerId);
    CustomerMemberLink current =
        customerMemberLinkRepository
            .findByCustomerIdAndStatus(customerId, LinkStatus.ACTIVE)
            .orElseThrow(() -> new ConflictException("関連状態が変わりました。再取得して確認してください"));
    requireExpectedLink(current, expectedLinkId);
    current.release(actorId, operationReason, OffsetDateTime.now());
    customerMemberLinkRepository.save(current);
  }

  /**
   * 顧客に現に有効な紐づけ。無ければ 404 — 「紐づいていない」を 200 で表すと、呼出側が本体を読んでから もう一度分岐することになり、操作の可否を状態の有無だけで決められなくなる。
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public CustomerMemberLinkResponse current(String customerId) {
    requireCustomer(customerId);
    return customerMemberLinkRepository
        .findByCustomerIdAndStatus(customerId, LinkStatus.ACTIVE)
        .map(
            link ->
                new CustomerMemberLinkResponse(
                    link.getId(), true, link.getMemberCode(), link.getLinkedAt()))
        .orElseThrow(() -> new NotFoundException("紐づけられている会員がいません"));
  }

  /**
   * 顧客 1 件の紐づけ履歴。続きはカーソルで辿る。
   *
   * <p>履歴は解除・再紐づけのたびに増え続けるので、上限の無い一覧では返さない。
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<CustomerMemberLinkHistoryResponse> history(
      String customerId, String cursor, int requestedSize) {
    requireCustomer(customerId);
    int size = CursorPage.clampSize(requestedSize);
    // 続きの有無は上限より 1 件多く取って判る。総件数の問い合わせを毎回撒かずに済む。
    Limit limit = Limit.of(size + 1);
    List<CustomerMemberLinkView> fetched =
        cursor == null
            ? customerMemberLinkRepository.findHistory(customerId, limit)
            : fetchHistoryAfter(customerId, PageCursor.decode(cursor), limit);
    return CursorPage.of(fetched, size, CustomerMemberLinkService::cursorOf)
        .map(CustomerMemberLinkService::toHistoryResponse);
  }

  private List<CustomerMemberLinkView> fetchHistoryAfter(
      String customerId, PageCursor cursor, Limit limit) {
    return customerMemberLinkRepository.findHistoryAfter(
        customerId, cursor.timestampKey(), cursor.id(), limit);
  }

  /** 続きの位置は一覧の並び（紐づけ時刻 + id）と同じ組で作る。組が並びとずれると、続きが手前へ戻るか行を飛ばす。 */
  private static String cursorOf(CustomerMemberLinkView view) {
    return new PageCursor(view.getLinkedAt().toString(), view.getId()).encode();
  }

  private void requireCustomer(String customerId) {
    if (!customerRepository.existsById(customerId)) {
      throw new NotFoundException("顧客が見つかりません");
    }
  }

  /** 成立・変更・解除は名指された顧客行を直列化点とする。他店舗は 404、統合済みは 409 とし、 操作対象を暗黙に存続顧客へ向け直さない。 */
  private void lockEditableCustomer(String customerId) {
    customerRepository
        .findByIdForUpdate(customerId)
        .orElseThrow(() -> new NotFoundException("顧客が見つかりません"));
    if (customerRepository.isMerged(customerId)) {
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
