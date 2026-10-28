package com.kizuna.order.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kizuna.order.domain.ReceptionRoute;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 店舗が起こす受注の内容。試算と作成の双方がこの型を署名の入力にする。 */
public record OrderCreateCommand(
    String replacementForOrderId,
    List<String> specialServiceIds,
    @JsonIgnore String confirmationToken,
    String courseId,
    Long receptionistId,
    LocalDate businessDate,
    LocalTime arrivalScheduledStartTime,
    LocalTime arrivalScheduledEndTime,
    CustomerSelection customerSelection,
    ContactSnapshotInput contactSnapshot,
    List<BusinessContactPermissionInput> businessContactPermissions,
    String address,
    String buildingName,
    String castId,
    Integer pax,
    List<OrderFeeLineInput> feeLines,
    ReceptionRoute receptionRoute,
    String carrier,
    String mediaName,
    String remarks,
    String castDriverMessage) {}
