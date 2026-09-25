package com.kizuna.order.application;

import com.kizuna.customer.contact.GuestContactImport;
import com.kizuna.customer.contact.GuestContactImports;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.order.api.dto.BusinessContactPermissionRequest;
import com.kizuna.order.api.dto.BusinessContactPermissionResponse;
import com.kizuna.order.api.dto.CustomerSelectionRequest;
import com.kizuna.order.api.dto.OrderApplicationConfirmationRequest;
import com.kizuna.order.contact.BusinessContactPolicy;
import com.kizuna.order.domain.ContactSnapshot;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.shared.exception.ServiceException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GuestApplicationConsent {
  private final BusinessContactPolicy policy;
  private final GuestContactImports imports;

  public List<GuestContactImport> previewImports(
      OrderApplication application, OrderApplicationConfirmationRequest request) {
    var input = request.getContactImports();
    if (!application.isGuest()) {
      if (input != null) throw new ServiceException("会員申請ではゲスト同意を取り込めません");
      return List.of();
    }
    if (input == null) throw new ServiceException("連絡先を取り込むか明示してください");
    var selection = request.getCustomerSelection();
    if (selection == null) throw new ServiceException("顧客の選択方法を指定してください");
    if (!input.isEmpty() && selection.mode() == CustomerSelectionRequest.Mode.NONE)
      throw new ServiceException("取り込み先の顧客を選択してください");
    var seen = EnumSet.noneOf(ContactType.class);
    var results = new ArrayList<GuestContactImport>();
    for (var item : input) {
      if (item == null
          || item.type() == null
          || item.importMarketingConsent() == null
          || !seen.add(item.type())) throw new ServiceException("取り込む連絡先の種類と同意の扱いを重複なく指定してください");
      var value = BusinessContactPermissions.value(application.getConsentContact(), item.type());
      if (value == null) throw new ServiceException("申請に存在する連絡先を選択してください");
      if (item.importMarketingConsent() && !application.getContactConsent().marketingAllowed())
        throw new ServiceException("申請で販促同意が選択されていません");
      results.add(
          imports.preview(
              selection.customerId(),
              item.contactId(),
              item.type(),
              value,
              item.importMarketingConsent()));
    }
    return results;
  }

  public void importContacts(
      OrderApplication application,
      String customerId,
      List<GuestContactImport> preview,
      Long actorId) {
    var results = new ArrayList<GuestContactImport>();
    for (var item : preview) {
      var consent = application.getContactConsent();
      results.add(
          imports.record(
              customerId,
              item,
              application.getId(),
              consent.marketingText() + " 取得日時: " + consent.acquiredAt(),
              actorId));
    }
    application.recordContactImports(results);
  }

  public List<BusinessContactPermissionResponse> describe(OrderApplication application) {
    var result = new ArrayList<BusinessContactPermissionResponse>();
    if (!application.isGuest()) return result;
    var consent = application.getContactConsent();
    for (var type : ContactType.values()) {
      var value = BusinessContactPermissions.value(application.getConsentContact(), type);
      if (value == null) continue;
      var status =
          consent.businessAllowed()
              ? ContactPermissionStatus.ALLOWED
              : ContactPermissionStatus.UNKNOWN;
      result.add(
          new BusinessContactPermissionResponse(
              type,
              value,
              status,
              source(application),
              reason(application),
              null,
              consent.acquiredAt(),
              policy.evaluate(type, value, status)));
    }
    return result;
  }

  public List<BusinessContactPermissionRequest> forOrder(
      OrderApplication application, ContactSnapshot snapshot) {
    var result = new ArrayList<BusinessContactPermissionRequest>();
    if (!application.isGuest()) return result;
    for (var type : ContactType.values()) {
      var original = BusinessContactPermissions.value(application.getConsentContact(), type);
      if (original != null && original.equals(BusinessContactPermissions.value(snapshot, type)))
        result.add(
            new BusinessContactPermissionRequest(
                type, ContactPermissionStatus.ALLOWED, source(application), reason(application)));
    }
    return result;
  }

  private String source(OrderApplication application) {
    return "ゲスト申請 " + application.getId();
  }

  private String reason(OrderApplication application) {
    var consent = application.getContactConsent();
    return consent.businessText() + " 取得日時: " + consent.acquiredAt();
  }
}
