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
import com.kizuna.order.infrastructure.OrderSearchQuery.OrderedRow;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
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
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
   * 作業キュー（対応が要る受注）の読み口。状態の群を指定して、検索と並び替えを当てたうえでカーソルで辿る。
   *
   * <p>絞り込みは DB 側で行う — 受注一覧の先頭ページを取って手元で選り分けると、完了が積み上がった店舗で 未対応の受注が取得窓から落ちて見えなくなる。
   *
   * <p>続きの指定は「何件目か」ではなくカーソル（並びの鍵）で受ける。この一覧は行が処理で消えていく作業キューなので、
   * 件数で位置を指すと確定・取消のたびに後続が繰り上がり、続きを取った時点で境界の受注を飛ばす。
   *
   * <p>取得は 2 段。条件と並びは要求ごとに変わるので ID の並びだけを動的な問い合わせで確定させ、行の中身は 表示名の join を持つ既存の読み口で引き直す（{@link
   * OrderSearchQuery} にその理由を記す）。
   *
   * <p><b>2 段は 1 つの断面で読む</b>（{@code REPEATABLE READ}）。既定の READ COMMITTED では文ごとに断面を取り直すため、 2
   * 本の間に他の操作者の commit が挟まると 2 本が違う世界を見る — 境界行の並び鍵が書き換われば続きが行を飛ばし、
   * 状態が終端へ動けば作業キューの応答に完了・取消済みの行が混じる（画面はそれを確定として描き、押せない操作を出し続ける）。 変種を 1 つずつ塞ぐのではなく、断面を 1
   * つにして根を断つ。読み取り専用なので直列化の失敗は起こらない。
   *
   * <p>射程はこのトランザクションの中だけである。要求をまたぐページ送り（アーカイブのオフセット）は別の問題で、 隔離水準では解けない。
   *
   * @param cursor 続きの位置。null なら先頭から
   * @param requestedSize 1 回に返す件数の希望値（上限に丸められる）
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CursorPage<OrderView> listWorkQueue(
      Long storeId, OrderQueryCriteria criteria, String cursor, int requestedSize) {
    int size = CursorPage.clampSize(requestedSize);
    // 続きの有無は上限より 1 件多く取って判る。総件数の問い合わせを毎回撒かずに済む。
    List<OrderedRow> rows =
        orderSearchQuery.findRows(
            storeId, criteria, cursor == null ? null : PageCursor.decode(cursor), size + 1);
    // 続きの位置は並びを決めたこの問い合わせが返した鍵から組む。行の中身を引く 2 本目から組むと、
    // 2 本が別の断面を見た場合に並べたときの値と食い違い、間の受注を丸ごと飛ばす（OrderedRow に理由を記す）。
    Map<String, String> cursorsById =
        rows.stream()
            .collect(Collectors.toMap(OrderedRow::id, row -> String.valueOf(row.sortKey())));
    List<OrderView> views = viewsInOrder(storeId, rows.stream().map(OrderedRow::id).toList());
    return CursorPage.of(views, size, view -> cursorOf(view, cursorsById));
  }

  /**
   * アーカイブ（完了・取消）の読み口。作業キューと同じ検索・並び替えを、オフセットのページャで辿る。
   *
   * <p>位置をページ番号で指せるのは、終端状態の受注が処理で消えず増えるだけだからである。
   *
   * <p>ここも 3 本（並び・本体・総件数）を 1 つの断面で読む（{@link #listWorkQueue} と同じ理由）。件数だけが別の断面から来ると、
   * 表示中の行数とページャの主張する総数が食い違う。
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<OrderView> listArchive(Long storeId, OrderQueryCriteria criteria, Pageable pageable) {
    // アーカイブは位置をページ番号で指すため、鍵の値そのものは要らない
    List<String> ids = orderSearchQuery.findIds(storeId, criteria, pageable);
    return new PageImpl<>(
        viewsInOrder(storeId, ids), pageable, orderSearchQuery.count(storeId, criteria));
  }

  /**
   * 名指された ID の読み側 projection を、渡された ID の並びのまま返す。
   *
   * <p>1 件でも引けなければ<b>失敗させる</b>。黙って取り落とすと、作業キューでは「上限より 1 件多く取れたか」で判定している 続きの有無がその 1
   * 件ぶん狂い、続きがあるのに「もう無い」と返る — 呼出側はそこで読むのをやめるので、 対応が要る受注が画面から永久に消える。件数の減った 1
   * ページとして現れるため、断らなければ表示上は正常に見えてしまう。
   *
   * <p>2 つの問い合わせは同じ店舗・同じトランザクションで走るので、母集合が食い違うこと自体が実装の欠陥である （典型は片方の問い合わせに店舗条件が欠けている場合）。
   *
   * <p>捕まえるのは<b>行が消えたこと</b>だけで、行の値が変わったことは見ない。READ COMMITTED では 2 本が別の断面を
   * 見るため値の食い違い自体は起こりうるが、続きの位置は並びを決めた 1 本目の鍵から組むので（{@code OrderSearchQuery.OrderedRow}）、表示が 1
   * 拍古いことしか意味しない。
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

  /** 続きの位置は一覧の並び（並び替えの鍵 + id）と同じ組で作る。組が並びとずれると、続きが手前へ戻るか行を飛ばす。 */
  private static String cursorOf(OrderView view, Map<String, String> cursorsById) {
    return new PageCursor(cursorsById.get(view.getId()), view.getId()).encode();
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

  /**
   * 予約受付箱（申請一覧）の読み口。状態の群を指定してカーソルで辿る（受付箱は PENDING）。
   *
   * <p>行が処理で消えていく一覧をカーソルで辿る作法（位置を件数で指さない・上限より 1 件多く取る・ 続きの位置は並びと同じ組で作る）は {@link #listWorkQueue}
   * と同じ。
   *
   * @param cursor 続きの位置。null なら先頭から
   * @param requestedSize 1 回に返す件数の希望値（上限に丸められる）
   */
  @Transactional(readOnly = true)
  public CursorPage<OrderApplicationItem> listApplications(
      Long storeId, Set<OrderApplicationStatus> statuses, String cursor, int requestedSize) {
    int size = CursorPage.clampSize(requestedSize);
    Limit limit = Limit.of(size + 1);
    List<OrderApplicationView> fetched =
        cursor == null
            ? orderApplicationRepository.findViews(storeId, statuses, limit)
            : fetchApplicationsAfter(storeId, statuses, PageCursor.decode(cursor), limit);
    LocalDate today = businessDateService.currentBusinessDate();
    return CursorPage.of(fetched, size, OrderQueryService::applicationCursorOf)
        .map(view -> applicationItem(view, today));
  }

  private List<OrderApplicationView> fetchApplicationsAfter(
      Long storeId, Set<OrderApplicationStatus> statuses, PageCursor cursor, Limit limit) {
    return orderApplicationRepository.findViewsAfter(
        storeId, statuses, cursor.dateKey(), cursor.id(), limit);
  }

  private static String applicationCursorOf(OrderApplicationView view) {
    return new PageCursor(view.getBusinessDate().toString(), view.getId()).encode();
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
  public CursorPage<OrderCustomerCandidate> customerCandidates(
      Long storeId, String search, String cursor, int size, boolean canReadContacts) {
    if (size < 1 || size > 100 || (cursor != null && !cursor.matches("[0-9]{1,64}")))
      throw new ServiceException("検索の件数またはカーソルを確認してください");
    Specification<Customer> spec =
        (root, query, cb) -> {
          var predicates = new ArrayList<Predicate>();
          predicates.add(cb.equal(root.get("storeId"), storeId));
          predicates.add(cb.isNull(root.get("mergedIntoId")));
          if (cursor != null) predicates.add(cb.greaterThan(root.get("id"), cursor));
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
    var candidates =
        customerRepository
            .findAll(spec, PageRequest.of(0, size + 1, Sort.by(Customer::getId)))
            .getContent();
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
    var rows =
        candidates.stream()
            .map(
                c ->
                    new OrderCustomerCandidate(
                        c.getId(), c.getName(), phoneByCustomer.get(c.getId())))
            .toList();
    return CursorPage.of(rows, size, OrderCustomerCandidate::id);
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
