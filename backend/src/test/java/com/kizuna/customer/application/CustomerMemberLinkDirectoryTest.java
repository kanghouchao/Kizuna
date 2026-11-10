package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.customer.domain.MemberLinkDirectoryView;
import com.kizuna.shared.storescope.StoreScope;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerMemberLinkDirectoryTest {

  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-26T10:00:00+09:00");

  @Mock private CustomerMemberLinkRepository repository;

  @InjectMocks private CustomerMemberLinkDirectory directory;

  private record View(
      String id,
      Long storeId,
      String storeName,
      String customerId,
      String customerName,
      LinkStatus status,
      LinkReason reason,
      OffsetDateTime linkedAt,
      OffsetDateTime releasedAt,
      String releaseReason)
      implements MemberLinkDirectoryView {
    @Override
    public String getId() {
      return id;
    }

    @Override
    public Long getStoreId() {
      return storeId;
    }

    @Override
    public String getStoreName() {
      return storeName;
    }

    @Override
    public String getCustomerId() {
      return customerId;
    }

    @Override
    public String getCustomerName() {
      return customerName;
    }

    @Override
    public LinkStatus getStatus() {
      return status;
    }

    @Override
    public LinkReason getReason() {
      return reason;
    }

    @Override
    public OffsetDateTime getLinkedAt() {
      return linkedAt;
    }

    @Override
    public OffsetDateTime getReleasedAt() {
      return releasedAt;
    }

    @Override
    public String getReleaseReason() {
      return releaseReason;
    }
  }

  @Test
  @DisplayName("店舗集合が空の閲覧者は区間を展開せず、関連を持つ店舗をすべて集合外として数える")
  void emptyScopeExpandsNothingAndCountsEveryStore() {
    when(repository.countStoresByMemberId(7L)).thenReturn(2L);

    var links = directory.findByMember(7L, new StoreScope(Set.of()));

    assertThat(links.segments()).isEmpty();
    assertThat(links.otherStoreCount()).isEqualTo(2);
    verify(repository, never()).findLatestDirectoryByMemberIdAndStoreIdIn(anyLong(), any());
    verify(repository, never()).countStoresByMemberIdAndStoreIdNotIn(anyLong(), any());
  }

  @Test
  @DisplayName("閲覧者は店舗集合内の店舗の最新区間だけを展開し、集合外は店舗数だけを返す")
  void specificStoresExpandsOnlyAuthorizedAndCountsTheRest() {
    when(repository.findLatestDirectoryByMemberIdAndStoreIdIn(7L, Set.of(1L)))
        .thenReturn(
            List.of(
                new View(
                    "L1",
                    1L,
                    "本店",
                    "C1",
                    "山田",
                    LinkStatus.ACTIVE,
                    LinkReason.MEMBER_CODE,
                    AT,
                    null,
                    null)));
    when(repository.countStoresByMemberIdAndStoreIdNotIn(7L, Set.of(1L))).thenReturn(2L);

    var links = directory.findByMember(7L, new StoreScope(Set.of(1L)));

    assertThat(links.segments())
        .singleElement()
        .satisfies(s -> assertThat(s.customerName()).isEqualTo("山田"));
    assertThat(links.otherStoreCount()).isEqualTo(2);
    verify(repository, never()).countStoresByMemberId(anyLong());
  }
}
