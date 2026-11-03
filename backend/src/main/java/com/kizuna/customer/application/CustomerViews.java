package com.kizuna.customer.application;

import com.kizuna.customer.domain.ContactPermissions;
import com.kizuna.customer.domain.ContactSummary;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.MergePreferences;
import com.kizuna.customer.domain.MergeProfile;
import com.kizuna.customer.domain.MergeSnapshot;
import java.time.LocalDate;
import java.util.List;

/** 顧客のユースケースが返す記録型。顧客行が持たない事実（紐づけ・受注件数・統合の標識）を行に添える。 */
public final class CustomerViews {
  private CustomerViews() {}

  /** 旧 ID で引かれたときだけ mergedFromId に要求された ID が入る。 */
  public record CustomerDetail(
      Customer customer,
      List<ContactSummary> preferredContacts,
      String linkedMemberCode,
      String mergedFromId) {}

  public record CustomerSummary(
      Customer customer,
      List<ContactSummary> preferredContacts,
      List<ContactSummary> matchedContacts,
      boolean memberLinked,
      LocalDate lastVisitDate,
      Long pointBalance) {}

  public record ComparisonRow(
      Customer customer,
      List<ContactSummary> preferredContacts,
      boolean memberLinked,
      long orderCount) {}

  /** 20 件を超える組の customers は空で、専用一覧で続きを読む。 */
  public record DuplicateGroup(
      ContactType matchedType, String matchedValue, long total, List<ComparisonRow> customers) {}

  /** effective は同じ種類・値の有効行どうしで合成した許諾。 */
  public record ContactView(CustomerContact contact, ContactPermissions effective) {}

  /** 未紐づけの顧客には台帳が無いので、残高は 0 ではなく null。 */
  public record PointBalance(boolean linked, Long balance) {}

  /** 統合の確認内容。確認値はこの記録型の JSON（previewToken は null）への署名なので、項目の名前・並び・型を変えない。 */
  public record MergePreview(
      MergeSnapshot surviving,
      MergeSnapshot merged,
      MergeProfile profile,
      MergePreferences preferredContacts,
      List<ContactType> preferenceConflicts,
      boolean memberLinked,
      String finalMemberCode,
      Long pointBalance,
      long unfinishedOrderCount,
      int movedOrderCount,
      int movedContactCount,
      int movedLinkCount,
      String previewToken) {}
}
