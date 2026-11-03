package com.kizuna.customer.api.dto;

import com.kizuna.customer.application.CustomerViews.ComparisonRow;
import com.kizuna.customer.application.CustomerViews.CustomerDetail;
import com.kizuna.customer.application.CustomerViews.CustomerSummary;
import com.kizuna.customer.application.CustomerViews.DuplicateGroup;
import com.kizuna.customer.domain.ContactSummary;
import com.kizuna.customer.domain.Customer;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

  // 会員紐づけは別集約の投影、統合の標識は「要求された ID が何だったか」で、どちらも写す元の行が持たない。
  @Mapping(target = "memberLinked", ignore = true)
  @Mapping(target = "linkedMemberCode", ignore = true)
  @Mapping(target = "merged", ignore = true)
  @Mapping(target = "mergedFromId", ignore = true)
  @Mapping(target = "preferredContacts", ignore = true)
  CustomerResponse toResponse(Customer customer);

  /**
   * memberLinked は関連状態の有無そのものなので常に真偽値が入る。統合の標識は旧 ID で引かれたときだけ載せる — 生きた行の応答には欄が現れない（non_null
   * 直列化）ので、欄の有無が「この ID は統合済みか」の答えになる。
   */
  default CustomerResponse toResponse(CustomerDetail detail) {
    CustomerResponse response = toResponse(detail.customer());
    response.setPreferredContacts(detail.preferredContacts());
    response.setMemberLinked(detail.linkedMemberCode() != null);
    response.setLinkedMemberCode(detail.linkedMemberCode());
    if (detail.mergedFromId() != null) {
      response.setMerged(true);
      response.setMergedFromId(detail.mergedFromId());
    }
    return response;
  }

  // 一覧は紐づけの有無だけを持ち、会員コードは載せない。
  @Mapping(target = "memberLinked", ignore = true)
  @Mapping(target = "preferredContacts", ignore = true)
  @Mapping(target = "matchedContacts", ignore = true)
  @Mapping(target = "lastVisitDate", ignore = true)
  @Mapping(target = "pointBalance", ignore = true)
  CustomerSummaryResponse toSummaryResponse(Customer customer);

  default CustomerSummaryResponse toSummaryResponse(CustomerSummary summary) {
    CustomerSummaryResponse row = toSummaryResponse(summary.customer());
    row.setPreferredContacts(summary.preferredContacts());
    row.setMatchedContacts(summary.matchedContacts());
    row.setMemberLinked(summary.memberLinked());
    row.setLastVisitDate(summary.lastVisitDate());
    row.setPointBalance(summary.pointBalance());
    return row;
  }

  // 受注件数と紐づけの有無は顧客行が持たない事実なので、引数で受ける。
  @Mapping(target = "memberLinked", source = "memberLinked")
  @Mapping(target = "orderCount", source = "orderCount")
  CustomerMergeComparisonResponse toComparisonResponse(
      Customer customer,
      boolean memberLinked,
      long orderCount,
      List<ContactSummary> preferredContacts);

  default CustomerMergeComparisonResponse toComparisonResponse(ComparisonRow row) {
    return toComparisonResponse(
        row.customer(), row.memberLinked(), row.orderCount(), row.preferredContacts());
  }

  default CustomerDuplicateGroupResponse toDuplicateGroupResponse(DuplicateGroup group) {
    return new CustomerDuplicateGroupResponse(
        group.matchedType(),
        group.matchedValue(),
        group.total(),
        group.customers().stream().map(this::toComparisonResponse).toList());
  }
}
