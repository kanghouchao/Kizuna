package com.kizuna.order.application;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerContactSearch;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.order.domain.OrderApplicationView;
import com.kizuna.order.domain.OrderFeeLineSnapshot;
import com.kizuna.order.domain.OrderQueryCriteria;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderView;
import com.kizuna.order.infrastructure.OrderSearchQuery;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.application.ReceptionistEligibilityService;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

  private final OrderRepository orderRepository;
  private final OrderApplicationRepository orderApplicationRepository;
  private final OrderSearchQuery orderSearchQuery;
  private final CustomerRepository customerRepository;
  private final CustomerContactRepository customerContactRepository;
  private final NominatableCastLookup nominatableCast;
  private final ReceptionistEligibilityService receptionistEligibilityService;
  private final BusinessDateService businessDateService;
  private final GuestApplicationConsent guestConsent;
  private final BusinessContactPermissions businessContactPermissions;
  private final OrderSpecialServices specialServices;

  @Transactional(readOnly = true)
  public Page<OrderView> list(Long storeId, String customerId, Pageable pageable) {
    // 一覧は集約を経由せず JPQL join projection で取得。customerId は顧客詳細の注文履歴用
    String filter = (customerId == null || customerId.isBlank()) ? null : customerId;
    return orderRepository.findAllViews(storeId, filter, pageable);
  }

  /**
   * 作業キュー（対応が要る受注）の読み口。状態の群を指定して、検索と並び替えを当てたうえでページで辿る。
   *
   * <p>絞り込みは DB 側で行う — 受注一覧の先頭ページを取って手元で選り分けると、完了が積み上がった店舗で 未対応の受注が取得窓から落ちて見えなくなる。
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<OrderView> listWorkQueue(
      Long storeId, OrderQueryCriteria criteria, Pageable pageable) {
    return search(storeId, criteria, pageable);
  }

  /** アーカイブ（完了・取消）の読み口。作業キューと同じ検索・並び替えを、ページで辿る。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<OrderView> listArchive(Long storeId, OrderQueryCriteria criteria, Pageable pageable) {
    return search(storeId, criteria, pageable);
  }

  /**
   * 並び（ID）・本体・総件数の 3 本を、呼出側の {@code REPEATABLE READ} の 1 断面で読む。条件と並びは要求ごとに変わるので ID
   * の並びだけを動的な問い合わせで確定させ、行の中身は表示名の join を持つ既存の読み口で引き直す（{@link OrderSearchQuery}）。 断面が 3
   * 本で違うと、状態が終端へ動いた行が作業キューに混じり、表示中の行数とページャの総数が食い違う。
   */
  private Page<OrderView> search(Long storeId, OrderQueryCriteria criteria, Pageable pageable) {
    List<String> ids = orderSearchQuery.findIds(storeId, criteria, pageable);
    return new PageImpl<>(
        viewsInOrder(storeId, ids), pageable, orderSearchQuery.count(storeId, criteria));
  }

  /**
   * 名指された ID の読み側 projection を、渡された ID の並びのまま返す。
   *
   * <p>1 件でも引けなければ<b>失敗させる</b>。2 つの問い合わせは同じ店舗・同じ断面で走るので、母集合の食い違いは実装の欠陥である （典型は片方の
   * 問い合わせに店舗条件が欠けている場合）。黙って取り落とすと件数の減った 1 ページとして正常に見えてしまう。
   */
  private List<OrderView> viewsInOrder(Long storeId, List<String> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<String, OrderView> byId =
        orderRepository.findViewsByIds(storeId, ids).stream()
            .collect(Collectors.toMap(OrderView::getId, view -> view));
    List<OrderView> views = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    if (views.size() != ids.size()) {
      throw new IllegalStateException(
          "受注一覧の並びと本体で母集合が食い違いました: 並び %d 件に対し本体 %d 件".formatted(ids.size(), views.size()));
    }
    return views;
  }

  /** 集約・表示用 projection・現在の受諾と要対応を同じ断面から返す。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public OrderDetail get(Long storeId, String id) {
    return toDetail(
        storeId,
        orderRepository
            .findByIdAndStoreId(id, storeId)
            .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id)));
  }

  /** 予約受付箱（申請一覧）の読み口。状態の群を指定してページで辿る（受付箱は PENDING）。 */
  @Transactional(readOnly = true)
  public Page<OrderApplicationItem> listApplications(
      Long storeId, Set<OrderApplicationStatus> statuses, Pageable pageable) {
    LocalDate today = businessDateService.currentBusinessDate();
    return orderApplicationRepository
        .findViews(storeId, statuses, pageable)
        .map(view -> applicationItem(view, today));
  }

  @Transactional(readOnly = true)
  public OrderApplicationDetail applicationDetail(Long storeId, String id) {
    var application =
        orderApplicationRepository
            .findByIdAndStoreId(id, storeId)
            .orElseThrow(() -> new NotFoundException("予約申請が見つかりません"));
    var view =
        orderApplicationRepository
            .findView(storeId, id)
            .orElseThrow(() -> new NotFoundException("予約申請が見つかりません"));
    return new OrderApplicationDetail(
        applicationItem(view, businessDateService.currentBusinessDate()),
        application.getContactConsent(),
        guestConsent.describe(application),
        List.copyOf(application.getContactImports()),
        application.getProcessedBy(),
        application.getProcessedAt());
  }

  private static OrderApplicationItem applicationItem(
      OrderApplicationView view, LocalDate currentBusinessDate) {
    return new OrderApplicationItem(
        view,
        OrderApplication.isExpired(view.getStatus(), view.getBusinessDate(), currentBusinessDate));
  }

  @Transactional(readOnly = true)
  public Page<OrderCustomerCandidate> customerCandidates(
      Long storeId, String search, boolean canReadContacts, Pageable pageable) {
    Specification<Customer> spec =
        (root, query, cb) -> {
          var predicates = new ArrayList<Predicate>();
          predicates.add(cb.equal(root.get("storeId"), storeId));
          predicates.add(cb.isNull(root.get("mergedIntoId")));
          if (search != null && !search.isBlank()) {
            String literalTerm =
                "%" + search.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            var names = cb.like(root.get("name"), literalTerm, '!');
            if (canReadContacts) {
              var sq = query.subquery(String.class);
              var contact = sq.from(CustomerContact.class);
              sq.select(contact.get("customerId"))
                  .where(
                      cb.equal(contact.get("customerId"), root.get("id")),
                      cb.equal(contact.get("storeId"), root.get("storeId")),
                      cb.isFalse(contact.get("deleted")),
                      CustomerContactSearch.matches(contact, cb, search));
              predicates.add(cb.or(names, cb.exists(sq)));
            } else predicates.add(names);
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    var candidates = customerRepository.findAll(spec, pageable);
    var phoneByCustomer =
        canReadContacts
            ? customerContactRepository
                .findByStoreIdAndCustomerIdInAndPreferredTrueAndDeletedFalseOrderByIdAsc(
                    storeId, candidates.stream().map(Customer::getId).toList())
                .stream()
                .filter(c -> c.getType() == ContactType.PHONE)
                .collect(
                    Collectors.toMap(CustomerContact::getCustomerId, CustomerContact::getValue))
            : Map.<String, String>of();
    return candidates.map(
        c -> new OrderCustomerCandidate(c.getId(), c.getName(), phoneByCustomer.get(c.getId())));
  }

  /**
   * 指名候補の一覧（当店に在籍中のキャストを名前で絞り込んだもの）。書き込み時の指名検証と同一の述語（{@link NominatableCastLookup}）を共有する。
   *
   * <p>キャスト管理の一覧ではなくこの読み口を持つのは、指名が受注の操作だからで、候補の範囲も要る権限も受注側が決める。 管理一覧は在籍停止のキャストも返し、キャスト管理権限を要求する。
   *
   * @param search 名前の部分一致（任意）。未指定なら絞り込まない
   */
  @Transactional(readOnly = true)
  public List<OrderCastCandidate> listCastCandidates(Long storeId, String search) {
    return nominatableCast.searchCandidates(storeId, search).stream()
        .map(cast -> new OrderCastCandidate(cast.getEnrollmentId(), cast.getName()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<OrderReceptionistCandidate> listReceptionists(Long storeId) {
    return receptionistEligibilityService.listCandidates(storeId).stream()
        .map(candidate -> new OrderReceptionistCandidate(candidate.id(), candidate.displayName()))
        .toList();
  }

  /**
   * 受注 1 件の詳細。表示名の join を持つ読み側 projection と、行の集合を持つ集約の 2 本から組む — 1 行 1 受注の projection では明細を運べない。
   */
  OrderDetail toDetail(Long storeId, Order order) {
    OrderView view =
        orderRepository
            .findViewById(storeId, order.getId())
            .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + order.getId()));
    return new OrderDetail(
        view,
        order.getFeeLines().stream().map(OrderFeeLineSnapshot::of).toList(),
        order.getTotalRemuneration(),
        order.getTotalDurationMinutes(),
        businessContactPermissions.describe(order),
        specialServices.describe(storeId, order));
  }
}
