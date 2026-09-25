package com.kizuna.order.application;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.order.api.dto.BusinessContactHistoryResponse;
import com.kizuna.order.api.dto.BusinessContactPermissionRequest;
import com.kizuna.order.api.dto.BusinessContactPermissionResponse;
import com.kizuna.order.contact.BusinessContactPolicy;
import com.kizuna.order.domain.BusinessContactHistory;
import com.kizuna.order.domain.BusinessContactHistoryRepository;
import com.kizuna.order.domain.BusinessContactState;
import com.kizuna.order.domain.ContactSnapshot;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.application.ActorIdentityService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessContactPermissions {
  private final ActorIdentityService actors;
  private final BusinessContactHistoryRepository histories;
  private final OrderRepository orders;
  private final BusinessContactPolicy policy;

  public static String value(ContactSnapshot snapshot, ContactType type) {
    if (snapshot == null) return null;
    return switch (type) {
      case PHONE -> snapshot.phoneNumber();
      case EMAIL -> snapshot.email();
      case LINE -> snapshot.lineId();
    };
  }

  @StoreScopeExempt(reason = "要求の値だけを検証し、店舗のデータを読み書きしない")
  public void validate(ContactSnapshot snapshot, List<BusinessContactPermissionRequest> input) {
    requests(snapshot, input);
  }

  private Map<ContactType, BusinessContactPermissionRequest> requests(
      ContactSnapshot snapshot, List<BusinessContactPermissionRequest> input) {
    var result = new EnumMap<ContactType, BusinessContactPermissionRequest>(ContactType.class);
    if (input == null) return result;
    for (var item : input) {
      if (item == null
          || item.type() == null
          || item.status() == null
          || item.source() == null
          || item.source().isBlank()
          || item.source().length() > 200
          || item.reason() == null
          || item.reason().isBlank()
          || item.reason().length() > 2000) throw new ServiceException("今回の連絡可否・出所・根拠を入力してください");
      if (value(snapshot, item.type()) == null) throw new ServiceException("許可の対象となる連絡先を入力してください");
      if (result.put(item.type(), item) != null)
        throw new ServiceException("同じ種類の連絡可否は一度だけ指定してください");
    }
    return result;
  }

  @StoreScoped
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      Order order,
      ContactSnapshot previous,
      List<BusinessContactPermissionRequest> input,
      String actorEmail) {
    var requests = requests(order.getContactSnapshot(), input);
    for (var type : ContactType.values()) {
      String oldValue = value(previous, type), nextValue = value(order.getContactSnapshot(), type);
      var request = requests.get(type);
      if (request == null && Objects.equals(oldValue, nextValue)) continue;
      var latest =
          histories.findFirstByOrderIdAndTypeOrderByIdDesc(order.getId(), type).orElse(null);
      var before = latest == null ? BusinessContactState.unknown(oldValue) : latest.getAfter();
      var after =
          request == null
              ? BusinessContactState.unknown(nextValue)
              : new BusinessContactState(
                  nextValue, request.status(), request.source().strip(), request.reason().strip());
      histories.save(
          BusinessContactHistory.record(
              order.getId(),
              type,
              request == null ? "CONTACT_CHANGED" : "RECORDED",
              before,
              after,
              actors.requireUserId(actorEmail)));
      // 許可だけの編集も受注の版を進め、古い画面からの上書きを拒否する。
      order.setUpdatedAt(OffsetDateTime.now());
    }
  }

  @StoreScoped
  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<BusinessContactPermissionResponse> describe(Order order) {
    var result = new ArrayList<BusinessContactPermissionResponse>();
    for (var type : ContactType.values()) {
      String value = value(order.getContactSnapshot(), type);
      if (value == null) continue;
      var latest =
          histories.findFirstByOrderIdAndTypeOrderByIdDesc(order.getId(), type).orElse(null);
      var state = latest == null ? BusinessContactState.unknown(value) : latest.getAfter();
      if (state == null || !value.equals(state.value()))
        throw new IllegalStateException("受注の連絡先と許可記録が一致しません");
      result.add(
          new BusinessContactPermissionResponse(
              type,
              value,
              state.status(),
              state.source(),
              state.reason(),
              latest == null || state.source() == null ? null : latest.getRecordedBy(),
              latest == null || state.source() == null ? null : latest.getRecordedAt(),
              policy.evaluate(type, value, state.status())));
    }
    return result;
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<BusinessContactHistoryResponse> history(
      String orderId, String cursor, int requestedSize) {
    orders.findById(orderId).orElseThrow(() -> new NotFoundException("受注が見つかりません"));
    int size = CursorPage.clampSize(requestedSize);
    return CursorPage.of(
            histories.findByOrderIdAndIdLessThanOrderByIdDesc(
                orderId, cursor == null ? "~" : PageCursor.decodeKey(cursor), Limit.of(size + 1)),
            size,
            h -> PageCursor.encodeKey(h.getId()))
        .map(BusinessContactHistoryResponse::from);
  }
}
