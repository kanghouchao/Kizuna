package com.kizuna.order.api.dto;

import com.kizuna.order.application.BusinessContactPermissionInput;
import com.kizuna.order.application.BusinessContactPermissionState;
import com.kizuna.order.application.ContactSnapshotInput;
import com.kizuna.order.application.CustomerSelection;
import com.kizuna.order.application.DailyRemuneration;
import com.kizuna.order.application.GuestContactConsentText;
import com.kizuna.order.application.GuestContactImportInput;
import com.kizuna.order.application.GuestOrderApplicationCommand;
import com.kizuna.order.application.GuestOrderApplicationReceipt;
import com.kizuna.order.application.MemberOrderApplicationCommand;
import com.kizuna.order.application.MemberOrderApplicationResult;
import com.kizuna.order.application.MemberReceiptClaimResult;
import com.kizuna.order.application.MemberVisitSummary;
import com.kizuna.order.application.MonthlyRemuneration;
import com.kizuna.order.application.OrderApplicationConfirmationCommand;
import com.kizuna.order.application.OrderApplicationDetail;
import com.kizuna.order.application.OrderApplicationItem;
import com.kizuna.order.application.OrderAttributionCorrectionCommand;
import com.kizuna.order.application.OrderAttributionCorrectionProgress;
import com.kizuna.order.application.OrderAttributionState;
import com.kizuna.order.application.OrderCastCandidate;
import com.kizuna.order.application.OrderCompletionCommand;
import com.kizuna.order.application.OrderCorrectionInput;
import com.kizuna.order.application.OrderCorrectionOutcome;
import com.kizuna.order.application.OrderCreateCommand;
import com.kizuna.order.application.OrderCustomerCandidate;
import com.kizuna.order.application.OrderDetail;
import com.kizuna.order.application.OrderFeeLineInput;
import com.kizuna.order.application.OrderPointRollbackCommand;
import com.kizuna.order.application.OrderPointRollbackPreview;
import com.kizuna.order.application.OrderPoints;
import com.kizuna.order.application.OrderPreview;
import com.kizuna.order.application.OrderReceptionistCandidate;
import com.kizuna.order.application.OrderUpdateCommand;
import com.kizuna.order.application.SelfDailyRemuneration;
import com.kizuna.order.application.SelfMonthlyRemuneration;
import com.kizuna.order.application.SelfRemunerationChange;
import com.kizuna.order.application.SelfRemunerationDetail;
import com.kizuna.order.application.SelfRemunerationEnrollment;
import com.kizuna.order.application.SelfRemunerationLine;
import com.kizuna.order.application.SelfRemunerationOrder;
import com.kizuna.order.application.SelfRemunerationState;
import com.kizuna.order.application.SpecialServiceStatus;
import com.kizuna.order.domain.AdminOrderView;
import com.kizuna.order.domain.BusinessContactHistory;
import com.kizuna.order.domain.OrderFeeLineSnapshot;
import com.kizuna.order.domain.OrderSpecialServiceEvent;
import com.kizuna.order.domain.OrderView;
import com.kizuna.order.domain.RemunerationCastRow;
import com.kizuna.order.domain.RemunerationOrderRow;
import com.kizuna.order.domain.RemunerationStoreRow;
import com.kizuna.order.domain.SpecialServiceSnapshot;
import com.kizuna.point.application.PointLedgerService.PointRollbackHistory;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 注文エンティティとDTOのマッピングを行うMapStructマッパー。 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

  /** 読み側 projection をレスポンスDTOに変換します。明細は集約から別に載せます。 */
  @Mapping(target = "businessContactPermissions", ignore = true)
  @Mapping(target = "feeLines", ignore = true)
  @Mapping(target = "totalDurationMinutes", ignore = true)
  @Mapping(target = "totalRemuneration", ignore = true)
  @Mapping(target = "specialServices", ignore = true)
  OrderResponse toResponse(OrderView view);

  List<OrderFeeLineInput> toFeeLineInputs(List<OrderFeeLineRequest> requests);

  OrderCompletionCommand toCommand(OrderCompletionRequest request);

  /** 利用資格は会員への紐づけと同義（非会員にポイントは存在しない）。 */
  @Mapping(target = "redemptionEligible", source = "memberLinked")
  OrderPreviewResponse.Points toPointsResponse(OrderPoints points);

  /** 試算の結果を応答へ写す。新しく採用する明細の採用時刻は確定時に打ち直されるので、試算では返さない。 */
  default OrderPreviewResponse toPreviewResponse(OrderPreview preview) {
    var c = preview.course();
    var lines =
        toFeeLineResponses(preview.feeLines().stream().map(OrderFeeLineSnapshot::of).toList());
    lines.stream()
        .filter(line -> line.getLineId() == null)
        .forEach(line -> line.setAdoptedAt(null));
    int unresolved = preview.unresolvedSpecialServiceCount();
    return new OrderPreviewResponse(
        preview.confirmationToken(),
        new OrderPreviewResponse.CourseCondition(
            c.serviceId(),
            c.revisionId(),
            c.revisionNumber(),
            c.name(),
            c.durationMinutes(),
            c.price(),
            c.remuneration(),
            c.adoptionBasis()),
        lines,
        preview.totalFee(),
        preview.pointBasisAmount(),
        preview.totalDurationMinutes(),
        preview.totalRemuneration(),
        toPointsResponse(preview.points()),
        preview.specialServices().stream().map(this::toResponse).toList(),
        unresolved > 0,
        unresolved,
        preview.contactImports());
  }

  /** 明細をレスポンスへ変換します。保存されている帯符号金額を表示上の値へ翻します。 */
  default List<OrderFeeLineResponse> toFeeLineResponses(List<OrderFeeLineSnapshot> lines) {
    return lines.stream()
        .map(
            line ->
                OrderFeeLineResponse.builder()
                    .lineId(line.lineId())
                    .durationMinutes(line.durationMinutes())
                    .remuneration(line.remuneration())
                    .serviceId(line.serviceId())
                    .revisionId(line.adoption() == null ? null : line.adoption().revisionId())
                    .revisionNumber(
                        line.adoption() == null ? null : line.adoption().revisionNumber())
                    .adoptionBasis(line.adoption() == null ? null : line.adoption().adoptionBasis())
                    .adoptedAt(line.adoption() == null ? null : line.adoption().adoptedAt())
                    .kind(line.kind().name())
                    .name(line.name())
                    .amount(line.kind().displayedAmountOf(line.amount()))
                    .systemOwned(line.kind().isSystemOwned())
                    .build())
        .toList();
  }

  default OrderSpecialServiceResponse toResponse(SpecialServiceStatus status) {
    return new OrderSpecialServiceResponse(
        status.snapshot(), status.requiresAttention(), status.currentConsentStatus());
  }

  /** 訂正の前後の特殊サービスは同意の現況を引かない。 */
  default OrderSpecialServiceResponse toResponse(SpecialServiceSnapshot snapshot) {
    return new OrderSpecialServiceResponse(snapshot, false, null);
  }

  OrderCorrectionInput toInput(OrderCorrectionRequest request);

  default OrderCorrectionResponse toResponse(OrderCorrectionOutcome outcome) {
    var correction = outcome.correction();
    var before = outcome.before();
    var after = outcome.after();
    return new OrderCorrectionResponse(
        correction.getId(),
        before.totalFee(),
        after.totalFee(),
        before.course(),
        after.course(),
        before.totalRemuneration(),
        after.totalRemuneration(),
        before.totalDurationMinutes(),
        after.totalDurationMinutes(),
        toFeeLineResponses(before.feeLines()),
        toFeeLineResponses(after.feeLines()),
        before.specialServices().stream().map(this::toResponse).toList(),
        outcome.specialServices().stream().map(this::toResponse).toList(),
        correction.getOrderId(),
        correction.getStoreId(),
        correction.getBusinessDate(),
        correction.getCompletedAt(),
        correction.getCorrectedAt(),
        correction.getCorrectedBy(),
        correction.getReason(),
        correction.getBeforeVersion(),
        correction.getAfterVersion(),
        before.accruedRemuneration(),
        after.accruedRemuneration());
  }

  /** 読み側 projection を作業キューの行に変換します。 */
  OrderWorkQueueResponse toWorkQueueResponse(OrderView view);

  /** 読み側 projection をアーカイブの行に変換します。 */
  OrderArchiveResponse toArchiveResponse(OrderView view);

  /** 読み側 projection を顧客詳細の注文履歴の行に変換します。 */
  OrderSummaryResponse toSummaryResponse(OrderView view);

  /** 全群横断一覧の projection をレスポンスDTOに変換します（集合作用域）。 */
  AdminOrderResponse toAdminResponse(AdminOrderView view);

  CustomerSelection toSelection(CustomerSelectionRequest request);

  ContactSnapshotInput toInput(ContactSnapshotRequest request);

  BusinessContactPermissionInput toInput(BusinessContactPermissionRequest request);

  GuestContactImportInput toInput(GuestContactImportRequest request);

  OrderCreateCommand toCommand(OrderCreateRequest request);

  OrderUpdateCommand toCommand(OrderUpdateRequest request);

  OrderApplicationConfirmationCommand toCommand(OrderApplicationConfirmationRequest request);

  /** 詳細は読み側 projection に、集約から取り出した行の集合と応答時点の現況を載せて組む。 */
  default OrderResponse toResponse(OrderDetail detail) {
    OrderResponse response = toResponse(detail.view());
    response.setBusinessContactPermissions(
        detail.businessContactPermissions().stream().map(this::toResponse).toList());
    response.setSpecialServices(detail.specialServices().stream().map(this::toResponse).toList());
    response.setFeeLines(toFeeLineResponses(detail.feeLines()));
    response.setTotalRemuneration(detail.totalRemuneration());
    response.setTotalDurationMinutes(detail.totalDurationMinutes());
    return response;
  }

  BusinessContactPermissionResponse toResponse(BusinessContactPermissionState state);

  default BusinessContactHistoryResponse toResponse(BusinessContactHistory history) {
    return BusinessContactHistoryResponse.from(history);
  }

  default OrderSpecialServiceEventResponse toResponse(OrderSpecialServiceEvent event) {
    return OrderSpecialServiceEventResponse.of(event);
  }

  OrderCustomerCandidateResponse toResponse(OrderCustomerCandidate candidate);

  OrderCastCandidateResponse toResponse(OrderCastCandidate candidate);

  OrderReceptionistResponse toResponse(OrderReceptionistCandidate candidate);

  default OrderApplicationResponse toResponse(OrderApplicationItem item) {
    var view = item.view();
    return OrderApplicationResponse.builder()
        .id(view.getId())
        .businessDate(view.getBusinessDate())
        .arrivalScheduledStartTime(view.getArrivalScheduledStartTime())
        .pax(view.getPax())
        .castId(view.getCastId())
        .castName(view.getCastName())
        .remarks(view.getRemarks())
        .status(view.getStatus() == null ? null : view.getStatus().name())
        .requesterMemberCode(view.getRequesterMemberCode())
        .requesterDeclaredName(view.getRequesterDeclaredName())
        .contactSnapshot(view.getContactSnapshot())
        .orderId(view.getOrderId())
        .declinedReason(view.getDeclinedReason())
        .expired(item.expired())
        .build();
  }

  default OrderApplicationDetailResponse toResponse(OrderApplicationDetail detail) {
    return new OrderApplicationDetailResponse(
        toResponse(detail.application()),
        detail.contactConsent(),
        detail.businessContactPermissions().stream().map(this::toResponse).toList(),
        detail.contactImports().stream()
            .map(
                contact ->
                    new GuestContactImportResponse(
                        contact, detail.processedBy(), detail.processedAt()))
            .toList());
  }

  OrderPointRollbackCommand toCommand(OrderPointRollbackRequest request);

  OrderAttributionCorrectionCommand toCommand(OrderAttributionCorrectionRequest request);

  OrderAttributionCorrectionResponse toResponse(OrderAttributionCorrectionProgress progress);

  /** 訂正の残りは台帳を読む側が決めるので、現況の写しには載せない（{@code withPendingCorrection} で添える）。 */
  @Mapping(target = "pendingCorrectionAttributionId", ignore = true)
  @Mapping(target = "pendingCorrectionMemberCode", ignore = true)
  OrderAttributionResponse toResponse(OrderAttributionState state);

  default OrderReceiptTokenResponse toReceiptTokenResponse(String receiptToken) {
    return new OrderReceiptTokenResponse(receiptToken);
  }

  default OrderPointRollbackResponse toResponse(PointRollbackHistory history) {
    return OrderPointRollbackResponse.from(history);
  }

  default OrderPointRollbackPreviewResponse toResponse(OrderPointRollbackPreview preview) {
    return new OrderPointRollbackPreviewResponse(
        preview.alreadyRolledBack(),
        preview.memberCode(),
        preview.cancellablePoints(),
        preview.reversibleUsedPoints(),
        preview.currentTotalFee(),
        preview.offsetAmount(),
        preview.resultingTotalFee(),
        preview.rollback() == null ? null : toResponse(preview.rollback()));
  }

  GuestOrderApplicationCommand toCommand(GuestOrderApplicationCreateRequest request);

  GuestOrderApplicationResponse toResponse(GuestOrderApplicationReceipt receipt);

  GuestContactConsentTextResponse toResponse(GuestContactConsentText text);

  MemberOrderApplicationCommand toCommand(MemberOrderApplicationCreateRequest request);

  MemberOrderApplicationResponse toResponse(MemberOrderApplicationResult result);

  MemberVisitResponse toResponse(MemberVisitSummary summary);

  MemberReceiptClaimResponse toResponse(MemberReceiptClaimResult result);

  MonthlyRemunerationCastSummary toCastSummary(RemunerationCastRow row);

  MonthlyRemunerationOrderSummary toMonthlyOrderSummary(RemunerationOrderRow row);

  SelfMonthlyRemunerationOrderSummary toSelfMonthlyOrderSummary(RemunerationOrderRow row);

  SelfMonthlyRemunerationStoreSummary toStoreSummary(RemunerationStoreRow row);

  default DailyRemunerationResponse toResponse(DailyRemuneration result) {
    return new DailyRemunerationResponse(
        result.personId(),
        result.name(),
        result.businessDate(),
        result.totalRemuneration(),
        result.orders().map(this::toMonthlyOrderSummary));
  }

  default MonthlyRemunerationResponse toResponse(MonthlyRemuneration result) {
    return new MonthlyRemunerationResponse(
        result.personId(),
        result.name(),
        result.month(),
        result.totalRemuneration(),
        result.orders().map(this::toMonthlyOrderSummary));
  }

  default SelfDailyRemunerationResponse toResponse(SelfDailyRemuneration result) {
    return new SelfDailyRemunerationResponse(
        result.storeId(),
        result.storeName(),
        result.businessDate(),
        result.totalRemuneration(),
        result.orders().map(this::toSelfMonthlyOrderSummary));
  }

  default SelfMonthlyRemunerationResponse toResponse(SelfMonthlyRemuneration result) {
    return new SelfMonthlyRemunerationResponse(
        result.storeId(),
        result.storeName(),
        result.month(),
        result.totalRemuneration(),
        result.orders().map(this::toSelfMonthlyOrderSummary));
  }

  SelfRemunerationEnrollmentSummary toEnrollmentSummary(SelfRemunerationEnrollment enrollment);

  SelfRemunerationSummary toRemunerationSummary(SelfRemunerationOrder order);

  SelfRemunerationItem toItem(SelfRemunerationLine line);

  SelfRemunerationSnapshot toSnapshot(SelfRemunerationState state);

  @Mapping(target = "summary", source = "order")
  SelfRemunerationResponse toResponse(SelfRemunerationDetail detail);

  SelfRemunerationChangeResponse toResponse(SelfRemunerationChange change);
}
