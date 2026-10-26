package com.kizuna.customer.application;

import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.customer.domain.MemberLinkDirectoryView;
import com.kizuna.shared.storescope.StoreScope;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 会員起点で関連を読む、職員の会員コンソール向けの読み口。閲覧者の店舗集合に収まる区間だけを展開し、集合外は件数で返す — 店舗の 顧客台帳へは店舗集合を跨いで到達させない。 */
@Service
@RequiredArgsConstructor
@NamedInterface("application")
public class CustomerMemberLinkDirectory {

  private final CustomerMemberLinkRepository customerMemberLinkRepository;

  /** 展開した店舗ごとの最新の区間。顧客名は顧客行が消えていれば null。 */
  public record LinkSegment(
      String id,
      long storeId,
      String storeName,
      String customerId,
      String customerName,
      LinkStatus status,
      LinkReason reason,
      OffsetDateTime linkedAt,
      OffsetDateTime releasedAt,
      String releaseReason) {}

  /**
   * 会員の関連。
   *
   * @param segments 閲覧者の店舗集合内の店舗ごとの最新の区間（新しい順、解除済みを含む）
   * @param otherStoreCount 集合外で関連を持つ店舗の数
   */
  public record MemberLinks(List<LinkSegment> segments, long otherStoreCount) {}

  @Transactional(readOnly = true)
  public MemberLinks findByMember(long memberId, StoreScope scope) {
    if (scope.allStores()) {
      return new MemberLinks(
          toSegments(customerMemberLinkRepository.findLatestDirectoryByMemberId(memberId)), 0);
    }
    return new MemberLinks(
        toSegments(
            customerMemberLinkRepository.findLatestDirectoryByMemberIdAndStoreIdIn(
                memberId, scope.storeIds())),
        customerMemberLinkRepository.countStoresByMemberIdAndStoreIdNotIn(
            memberId, scope.storeIds()));
  }

  private static List<LinkSegment> toSegments(List<MemberLinkDirectoryView> views) {
    return views.stream()
        .map(
            view ->
                new LinkSegment(
                    view.getId(),
                    view.getStoreId(),
                    view.getStoreName(),
                    view.getCustomerId(),
                    view.getCustomerName(),
                    view.getStatus(),
                    view.getReason(),
                    view.getLinkedAt(),
                    view.getReleasedAt(),
                    view.getReleaseReason()))
        .toList();
  }
}
