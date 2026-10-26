package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.user.application.BusinessAudit;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerMemberLinkWithdrawalTest {

  @Mock private CustomerMemberLinkRepository repository;
  @Mock private BusinessAudit audit;

  @InjectMocks private CustomerMemberLinkWithdrawal withdrawal;

  private static CustomerMemberLink link(String id, long storeId, String customerId) {
    CustomerMemberLink link =
        CustomerMemberLink.builder()
            .customerId(customerId)
            .memberId(7L)
            .memberCode("123456789012")
            .reason(LinkReason.MEMBER_CODE)
            .linkedBy(42L)
            .linkedAt(OffsetDateTime.parse("2026-01-01T00:00:00+09:00"))
            .build();
    link.setId(id);
    link.setStoreId(storeId);
    return link;
  }

  @Test
  @DisplayName("全店舗の ACTIVE 関連を理由「会員退会」で解除し、1 件ずつ監査を残して件数を返す")
  void releasesEveryActiveLinkAcrossStores() {
    CustomerMemberLink first = link("l1", 1L, "c1");
    CustomerMemberLink second = link("l2", 2L, "c2");
    when(repository.findByMemberIdAndStatusOrderByIdAsc(7L, LinkStatus.ACTIVE))
        .thenReturn(List.of(first, second));
    OffsetDateTime at = OffsetDateTime.parse("2026-10-26T10:00:00+09:00");

    assertThat(withdrawal.releaseAll(7L, 10L, at)).isEqualTo(2);

    for (CustomerMemberLink link : List.of(first, second)) {
      assertThat(link.getStatus()).isEqualTo(LinkStatus.RELEASED);
      assertThat(link.getReleaseReason()).isEqualTo("会員退会");
      assertThat(link.getReleasedBy()).isEqualTo(10L);
      assertThat(link.getReleasedAt()).isEqualTo(at);
    }
    verify(repository).save(first);
    verify(repository).save(second);
    verify(repository).flush();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<java.util.Map<String, String>> after = ArgumentCaptor.forClass(Map.class);
    verify(audit, times(2))
        .recordById(
            eq(10L),
            any(),
            eq("CUSTOMER_MEMBER_LINK_RELEASED"),
            eq("CUSTOMER_MEMBER_LINK"),
            any(),
            eq("CUSTOMER"),
            any(),
            any(),
            after.capture());
    assertThat(after.getAllValues())
        .allSatisfy(snapshot -> assertThat(snapshot).containsEntry("status", "RELEASED"));
  }

  @Test
  @DisplayName("ACTIVE 関連が無ければ何も書かず 0 を返す")
  void nothingToReleaseReturnsZero() {
    when(repository.findByMemberIdAndStatusOrderByIdAsc(7L, LinkStatus.ACTIVE))
        .thenReturn(List.of());

    assertThat(withdrawal.releaseAll(7L, 10L, OffsetDateTime.now())).isZero();
    verify(repository, times(0)).save(any());
  }
}
