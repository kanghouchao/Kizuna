package com.kizuna.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerMemberLinkTest {

  private static final OffsetDateTime LINKED_AT = OffsetDateTime.parse("2026-08-01T10:00:00+09:00");

  private static CustomerMemberLink.CustomerMemberLinkBuilder validLink() {
    return CustomerMemberLink.builder()
        .customerId("c1")
        .memberId(7L)
        .memberCode("123456789012")
        .linkedBy(3L)
        .linkedAt(LINKED_AT);
  }

  @Test
  @DisplayName("構築直後は ACTIVE で、紐づけ時点の会員コードと実行者・日時を保持すること")
  void newLinkIsActiveAndKeepsSnapshot() {
    CustomerMemberLink link = validLink().build();

    assertThat(link.getStatus()).isEqualTo(LinkStatus.ACTIVE);
    assertThat(link.getCustomerId()).isEqualTo("c1");
    assertThat(link.getMemberId()).isEqualTo(7L);
    assertThat(link.getMemberCode()).isEqualTo("123456789012");
    assertThat(link.getLinkedBy()).isEqualTo(3L);
    assertThat(link.getLinkedAt()).isEqualTo(LINKED_AT);
    assertThat(link.getReleasedBy()).isNull();
    assertThat(link.getReleasedAt()).isNull();
  }

  @Test
  @DisplayName("顧客 ID が無い紐づけは構築できないこと")
  void blankCustomerIdIsRejected() {
    assertThatThrownBy(() -> validLink().customerId(" ").build())
        .isInstanceOf(InvalidCustomerMemberLinkException.class)
        .hasMessageContaining("顧客 ID");
  }

  @Test
  @DisplayName("会員 ID が無い紐づけは構築できないこと")
  void missingMemberIdIsRejected() {
    assertThatThrownBy(() -> validLink().memberId(null).build())
        .isInstanceOf(InvalidCustomerMemberLinkException.class)
        .hasMessageContaining("会員 ID");
  }

  @Test
  @DisplayName("会員コードが無い紐づけは構築できないこと（履歴のスナップショットが欠ける）")
  void blankMemberCodeIsRejected() {
    assertThatThrownBy(() -> validLink().memberCode("").build())
        .isInstanceOf(InvalidCustomerMemberLinkException.class)
        .hasMessageContaining("会員コード");
  }

  @Test
  @DisplayName("実行者が無い紐づけは構築できないこと（履歴の照会主体が欠ける）")
  void missingActorIsRejected() {
    assertThatThrownBy(() -> validLink().linkedBy(null).build())
        .isInstanceOf(InvalidCustomerMemberLinkException.class)
        .hasMessageContaining("実行者");
  }

  @Test
  @DisplayName("紐づけ日時が無い紐づけは構築できないこと")
  void missingLinkedAtIsRejected() {
    assertThatThrownBy(() -> validLink().linkedAt(null).build())
        .isInstanceOf(InvalidCustomerMemberLinkException.class)
        .hasMessageContaining("日時");
  }

  @Test
  @DisplayName("解除で RELEASED になり、解除の実行者と日時が記録されること")
  void releaseRecordsActorAndTimestamp() {
    CustomerMemberLink link = validLink().build();
    OffsetDateTime before = OffsetDateTime.now();

    link.release(9L);

    assertThat(link.getStatus()).isEqualTo(LinkStatus.RELEASED);
    assertThat(link.getReleasedBy()).isEqualTo(9L);
    assertThat(link.getReleasedAt()).isNotNull().isAfterOrEqualTo(before);
    // 紐づけ側の記録は解除で書き換わらない（区間の始点として履歴に残る）
    assertThat(link.getLinkedBy()).isEqualTo(3L);
    assertThat(link.getLinkedAt()).isEqualTo(LINKED_AT);
  }

  @Test
  @DisplayName("解除済みの区間は再度解除できないこと")
  void doubleReleaseIsRejected() {
    CustomerMemberLink link = validLink().build();
    link.release(9L);

    assertThatThrownBy(() -> link.release(9L))
        .isInstanceOf(InvalidCustomerMemberLinkException.class)
        .hasMessageContaining("既に解除されています");
  }

  @Test
  @DisplayName("実行者不明のままでは解除できないこと")
  void releaseWithoutActorIsRejected() {
    CustomerMemberLink link = validLink().build();

    assertThatThrownBy(() -> link.release(null))
        .isInstanceOf(InvalidCustomerMemberLinkException.class)
        .hasMessageContaining("実行者");

    assertThat(link.getStatus()).isEqualTo(LinkStatus.ACTIVE);
  }
}
