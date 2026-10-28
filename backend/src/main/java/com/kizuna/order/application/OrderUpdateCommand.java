package com.kizuna.order.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 受注の部分更新の内容（null は「変更しない」）。試算と更新の双方がこの型を署名の入力にする。 */
public record OrderUpdateCommand(
    List<String> specialServiceIds,
    String confirmationToken,
    String courseId,
    Long expectedVersion,
    Long receptionistId,
    LocalDate businessDate,
    LocalTime arrivalScheduledStartTime,
    LocalTime arrivalScheduledEndTime,
    String castId,
    Integer pax,
    List<OrderFeeLineInput> feeLines,
    String locationAddress,
    String locationBuilding,
    String carrier,
    String mediaName,
    String remarks,
    String castDriverMessage,
    CustomerSelection customerSelection,
    ContactSnapshotInput contactSnapshot,
    List<BusinessContactPermissionInput> businessContactPermissions) {}
