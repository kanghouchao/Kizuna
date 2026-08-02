package com.kizuna.customer.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/**
 * 会員と店舗顧客台帳の紐づけ。1 行が 1 区間（ACTIVE → RELEASED）を表す追加型の台帳で、解除・変更でも行は削除せず履歴として残す。
 *
 * <p>{@code memberCode} は紐づけ時点のスナップショットで、会員行が消えて {@code memberId} が欠落した後も履歴を読めるようにする。
 *
 * <p>不変条件（構築時に検証、違反は 400 系ドメイン例外）: 顧客 ID・会員 ID・会員コード・実行者・紐づけ日時が必須で、構築直後は必ず ACTIVE （{@link
 * InvalidCustomerMemberLinkException}）。
 */
@Entity
@Table(name = "t_customer_member_links")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
public class CustomerMemberLink extends StoreScopedEntity {

  @Column(name = "customer_id", nullable = false, updatable = false)
  private String customerId;

  @Column(name = "member_id", updatable = false)
  private Long memberId;

  @Column(name = "member_code", nullable = false, updatable = false, length = 20)
  private String memberCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private LinkStatus status;

  @Column(name = "linked_by", updatable = false)
  private Long linkedBy;

  @Column(name = "linked_at", nullable = false, updatable = false)
  private OffsetDateTime linkedAt;

  @Column(name = "released_by")
  private Long releasedBy;

  @Column(name = "released_at")
  private OffsetDateTime releasedAt;

  @Builder
  public CustomerMemberLink(
      String customerId, Long memberId, String memberCode, Long linkedBy, OffsetDateTime linkedAt) {
    if (customerId == null || customerId.isBlank()) {
      throw new InvalidCustomerMemberLinkException("顧客 ID は必須です");
    }
    if (memberId == null) {
      throw new InvalidCustomerMemberLinkException("会員 ID は必須です");
    }
    if (memberCode == null || memberCode.isBlank()) {
      throw new InvalidCustomerMemberLinkException("会員コードは必須です");
    }
    if (linkedBy == null) {
      throw new InvalidCustomerMemberLinkException("紐づけの実行者は必須です");
    }
    if (linkedAt == null) {
      throw new InvalidCustomerMemberLinkException("紐づけの日時は必須です");
    }
    this.customerId = customerId;
    this.memberId = memberId;
    this.memberCode = memberCode;
    this.linkedBy = linkedBy;
    this.linkedAt = linkedAt;
    this.status = LinkStatus.ACTIVE;
  }

  /** 紐づけを解除する。解除済みの区間は再度解除できない。 */
  public void release(Long actorId) {
    if (status != LinkStatus.ACTIVE) {
      throw new InvalidCustomerMemberLinkException("この紐づけは既に解除されています");
    }
    if (actorId == null) {
      throw new InvalidCustomerMemberLinkException("解除の実行者は必須です");
    }
    this.status = LinkStatus.RELEASED;
    this.releasedBy = actorId;
    this.releasedAt = OffsetDateTime.now();
  }

  @Override
  public String toString() {
    return "CustomerMemberLink(id="
        + getId()
        + ", customerId="
        + customerId
        + ", memberCode="
        + memberCode
        + ", status="
        + status
        + ")";
  }
}
