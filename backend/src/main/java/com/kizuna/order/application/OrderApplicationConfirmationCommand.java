package com.kizuna.order.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kizuna.order.domain.AcquisitionSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 予約申請の確定内容。試算と確定の双方がこの型を署名の入力にする。 */
public record OrderApplicationConfirmationCommand(
    List<GuestContactImportInput> contactImports,
    List<String> specialServiceIds,
    @JsonIgnore String confirmationToken,
    String courseId,
    List<OrderFeeLineInput> feeLines,
    Long receptionistId,
    LocalDate businessDate,
    LocalTime arrivalScheduledStartTime,
    LocalTime arrivalScheduledEndTime,
    String castId,
    Integer pax,
    String remarks,
    AcquisitionSource acquisitionSource,
    String mediumId,
    CustomerSelection customerSelection,
    ContactSnapshotInput contactSnapshot) {}
