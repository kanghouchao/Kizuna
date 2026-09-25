package com.kizuna.customer.application;

import com.kizuna.customer.api.dto.CustomerMergePreviewRequest;
import com.kizuna.customer.api.dto.CustomerMergePreviewResponse;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.customer.domain.MergePreferences;
import com.kizuna.customer.domain.MergeProfile;
import com.kizuna.customer.domain.MergeSnapshot;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MergePreparation {
  private final CustomerRepository customers;
  private final CustomerContactRepository contacts;
  private final CustomerMemberLinkRepository links;
  private final CustomerMergeRepository merges;
  private final PlatformUserRepository users;
  private final PointLedgerService points;
  private final MemberLookupService members;
  private final StoreContext store;
  private final MergeConfirmation confirmation;

  public record Prepared(CustomerMergePreviewResponse response, List<String> movedOrderIds) {}

  /** 呼出元が両顧客をロックし、受注の状態変更も快照取得から確定まで直列化する。 */
  @StoreScoped
  public Prepared prepare(String survivingId, CustomerMergePreviewRequest input) {
    var orders =
        merges.lockOrders(store.getStoreId(), List.of(survivingId, input.mergedCustomerId()));
    var surviving = snapshot(survivingId);
    var merged = snapshot(input.mergedCustomerId());
    var active =
        links.findByCustomerIdInAndStatus(
            List.of(survivingId, input.mergedCustomerId()), LinkStatus.ACTIVE);
    if (active.size() > 1) throw new ConflictException("両方の顧客に会員が紐づいています。先に理由付きで関連を解除してください");
    var allContacts =
        Stream.concat(surviving.contacts().stream(), merged.contacts().stream()).toList();
    var conflicts = new ArrayList<ContactType>();
    var selected = new ArrayList<String>();
    for (var type : ContactType.values()) {
      String id;
      if (input.preferredContacts() != null) {
        id = input.preferredContacts().selected(type);
        if (id != null
            && allContacts.stream()
                .noneMatch(c -> c.id().equals(id) && c.type() == type && !c.deleted()))
          throw new ServiceException("優先連絡先は対象顧客の同じ種類の有効な行を選択してください");
      } else {
        var preferred =
            allContacts.stream()
                .filter(c -> c.type() == type && c.preferred() && !c.deleted())
                .toList();
        if (preferred.size() > 1) conflicts.add(type);
        id = preferred.size() == 1 ? preferred.getFirst().id() : null;
      }
      selected.add(id);
    }
    var preferences =
        new MergePreferences(
            selected.get(ContactType.PHONE.ordinal()),
            selected.get(ContactType.EMAIL.ordinal()),
            selected.get(ContactType.LINE.ordinal()));
    var profile = input.profile() == null ? surviving.profile() : input.profile();
    var link = active.isEmpty() ? null : active.getFirst();
    if (link != null && link.getMemberId() != null)
      members.lockForBalanceConfirmation(link.getMemberId());
    Long balance =
        link == null || link.getMemberId() == null ? null : points.balance(link.getMemberId());
    var movedIds =
        orders.stream()
            .filter(o -> o.getCustomerId().equals(merged.id()))
            .map(CustomerMergeRepository.OrderState::getId)
            .toList();
    var result =
        new CustomerMergePreviewResponse(
            surviving,
            merged,
            profile,
            preferences,
            conflicts,
            link != null,
            link == null ? null : link.getMemberCode(),
            balance,
            orders.stream()
                .filter(
                    o -> o.getStatus().equals("CONFIRMED") || o.getStatus().equals("IN_SERVICE"))
                .count(),
            movedIds.size(),
            merged.contacts().size(),
            merged.memberLinks().size(),
            null);
    // 受注版を含め、画面上の件数が変わらない関連情報の変更も再確認対象にする。
    var versions =
        orders.stream()
            .map(o -> List.of(o.getId(), o.getCustomerId(), o.getStatus(), o.getVersion()))
            .toList();
    String token = conflicts.isEmpty() ? confirmation.sign(List.of(result, versions)) : null;
    return new Prepared(
        new CustomerMergePreviewResponse(
            surviving,
            merged,
            profile,
            preferences,
            conflicts,
            result.memberLinked(),
            result.finalMemberCode(),
            balance,
            result.unfinishedOrderCount(),
            result.movedOrderCount(),
            result.movedContactCount(),
            result.movedLinkCount(),
            token),
        movedIds);
  }

  @StoreScoped
  public MergeSnapshot snapshot(String id) {
    var rows = contacts.findByCustomerIdOrderByIdAsc(id);
    var contactStates =
        rows.stream()
            .map(
                c -> {
                  var effective =
                      rows.stream()
                          .filter(
                              other ->
                                  !other.isDeleted()
                                      && other.getType() == c.getType()
                                      && other.getValue().equals(c.getValue()))
                          .map(other -> other.permissions())
                          .reduce(c.permissions(), (a, b) -> a.restrict(b));
                  return MergeSnapshot.Contact.from(c, effective);
                })
            .toList();
    var intervals =
        links.findByCustomerIdOrderByIdAsc(id).stream()
            .map(
                l ->
                    MergeSnapshot.Link.from(
                        l, userName(l.getLinkedBy()), userName(l.getReleasedBy())))
            .toList();
    return new MergeSnapshot(
        id, MergeProfile.from(customers.findById(id).orElseThrow()), contactStates, intervals);
  }

  private String userName(Long id) {
    return id == null ? null : users.findById(id).map(PlatformUser::getDisplayName).orElse(null);
  }
}
