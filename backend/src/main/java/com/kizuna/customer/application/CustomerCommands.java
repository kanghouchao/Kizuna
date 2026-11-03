package com.kizuna.customer.application;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.MergePreferences;
import com.kizuna.customer.domain.MergeProfile;
import java.time.LocalDate;
import java.util.List;

/** 顧客のユースケースが受ける命令。入力の検証と前後の空白の除去は要求の側で済んでいる。 */
public final class CustomerCommands {
  private CustomerCommands() {}

  public record NewCustomer(
      String name,
      List<ContactValue> contacts,
      String address,
      String buildingName,
      String landmark,
      String classification,
      Boolean hasPet,
      String usageAreas,
      String ngType,
      String ngContent) {}

  public record ContactValue(ContactType type, String value) {}

  public record PermissionChange(ContactPermissionStatus status, String source, String reason) {}

  public record PointAdjustment(
      Integer delta, String reason, LocalDate expiresOn, String idempotencyKey) {}

  /** 統合の確認と実行で共通の選択。確認値は同じ選択から作り直して照合する。 */
  public record MergeSelection(
      String mergedCustomerId, MergeProfile profile, MergePreferences preferredContacts) {}

  public record MergeExecution(
      MergeSelection selection, String previewToken, String operationReason) {}
}
