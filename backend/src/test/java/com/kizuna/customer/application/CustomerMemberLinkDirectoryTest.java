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
  @DisplayName("全店舗の閲覧者は全区間を展開し、集合外の件数は 0 で数えもしない")
  void allStoresExpandsEverything() {
    when(repository.findDirectoryByMemberId(7L))
        .thenReturn(
            List.of(
                new View(
                    "L2",
                    2L,
                    "支店",
                    "C2",
                    "佐藤",
                    LinkStatus.RELEASED,
                    LinkReason.MEMBER_CODE,
                    AT.minusDays(1),
                    AT,
                    "会員退会"),
                new View(
                    "L1",
                    1L,
                    "本店",
                    "C1",
                    "山田",
                    LinkStatus.ACTIVE,
                    LinkReason.MEMBER_REQUEST,
                    AT.minusDays(2),
                    null,
                    null)));

    var links = directory.findByMember(7L, new StoreScope(true, Set.of()));

    assertThat(links.segments()).extracting(s -> s.storeName()).containsExactly("支店", "本店");
    assertThat(links.segments().getFirst().releaseReason()).isEqualTo("会員退会");
    assertThat(links.otherStoreCount()).isZero();
    verify(repository, never()).countByMemberIdAndStoreIdNotIn(anyLong(), any());
  }

  @Test
  @DisplayName("個別授権の閲覧者は授権店舗の区間だけを展開し、授権外は件数だけを返す")
  void specificStoresExpandsOnlyAuthorizedAndCountsTheRest() {
    when(repository.findDirectoryByMemberIdAndStoreIdIn(7L, Set.of(1L)))
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
    when(repository.countByMemberIdAndStoreIdNotIn(7L, Set.of(1L))).thenReturn(2L);

    var links = directory.findByMember(7L, new StoreScope(false, Set.of(1L)));

    assertThat(links.segments())
        .singleElement()
        .satisfies(s -> assertThat(s.customerName()).isEqualTo("山田"));
    assertThat(links.otherStoreCount()).isEqualTo(2);
    verify(repository, never()).findDirectoryByMemberId(anyLong());
  }
}
