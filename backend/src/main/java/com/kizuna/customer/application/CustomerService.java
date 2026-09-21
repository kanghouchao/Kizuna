package com.kizuna.customer.application;

import com.kizuna.customer.api.dto.ContactSummary;
import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerDuplicateGroupResponse;
import com.kizuna.customer.api.dto.CustomerMapper;
import com.kizuna.customer.api.dto.CustomerMergeComparisonResponse;
import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerSummaryResponse;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerCandidateRepository;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerContactSearch;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerOrderCountView;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.validation.ContactValues;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

  /** LIKE パターンのエスケープ規則。派生クエリが内部で使うものと同一で、手書きの cb.like にも同じ規則を適用する。 */
  private static final EscapeCharacter LIKE_ESCAPE = EscapeCharacter.DEFAULT;

  /** 初回応答に同梱する顧客数の上限。大きいグループは専用のカーソル一覧で読む。 */
  private static final int MAX_LISTED_GROUP_SIZE = 20;

  /** 見比べる対象は 2 行。3 行以上を一度に畳む形は持たない（ADR 0010）。 */
  private static final int COMPARISON_SIZE = 2;

  private static final String MERGED_CUSTOMER_UNDELETABLE =
      "統合に関与した顧客は削除できません。統合履歴と旧 ID の解決の根拠になります";

  private static final String ORDERED_CUSTOMER_UNDELETABLE = "受注が紐づいている顧客は削除できません。来店の記録が参照しています";

  /**
   * 墓標そのものを名指した書き換えの案内。更新・削除・ポイント調整・解除の 4 経路が同じ文面を返すのは、どれも次の一手が同じ
   * （統合先の行を編集する）だからである。統合先へ黙って向け直してよいのは参照の書き込み先だけで、名指した行への書き換えは撥ねる（ADR 0010）。
   */
  private static final String MERGED_CUSTOMER_NOT_EDITABLE = "統合済みの顧客です。統合先の顧客を編集してください";

  private final CustomerRepository customerRepository;
  private final CustomerCandidateRepository candidateRepository;
  private final CustomerContactService customerContactService;
  private final CustomerContactRepository customerContactRepository;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final CustomerMergeRepository customerMergeRepository;
  private final CustomerMapper customerMapper;

  /** 検索結果と一致理由を同じ断面から読み、途中の連絡先変更による不一致を避ける。 */
  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<CustomerSummaryResponse> list(
      String search, String classification, Pageable pageable) {
    Specification<Customer> spec = searchSpec(search, classification);
    Page<Customer> page = customerRepository.findAll(spec, pageable);
    // 会員紐づけは本ページ分だけを 1 回で引く（行ごとの追加問い合わせを作らない）。
    List<String> ids = page.getContent().stream().map(Customer::getId).toList();
    Map<String, String> activeCodes =
        ids.isEmpty()
            ? Map.of()
            : customerMemberLinkRepository
                .findByCustomerIdInAndStatus(ids, LinkStatus.ACTIVE)
                .stream()
                .collect(
                    Collectors.toMap(
                        CustomerMemberLink::getCustomerId, CustomerMemberLink::getMemberCode));
    var preferred = customerContactService.preferred(ids);
    Map<String, List<ContactSummary>> matched =
        ids.isEmpty() || search == null || search.isBlank()
            ? Map.of()
            : customerContactRepository
                .findAll(
                    (root, query, cb) ->
                        cb.and(
                            root.get("customerId").in(ids),
                            cb.isFalse(root.get("deleted")),
                            CustomerContactSearch.matches(root, cb, search)),
                    Sort.by("id"))
                .stream()
                .collect(
                    Collectors.groupingBy(
                        CustomerContact::getCustomerId,
                        Collectors.mapping(ContactSummary::from, Collectors.toList())));
    return page.map(
        customer -> {
          CustomerSummaryResponse row = customerMapper.toSummaryResponse(customer);
          row.setPreferredContacts(preferred.getOrDefault(customer.getId(), List.of()));
          row.setMatchedContacts(matched.getOrDefault(customer.getId(), List.of()));
          row.setMemberLinked(activeCodes.containsKey(customer.getId()));
          return row;
        });
  }

  /** グループ件数と判断材料を同じ断面から読み、途中の削除・統合による不一致を避ける。 */
  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CursorPage<CustomerDuplicateGroupResponse> listDuplicateCandidates(
      String search, ContactType type, String cursor, int requestedSize) {
    int size = candidatePageSize(requestedSize);
    ContactType afterType = null;
    String afterValue = null;
    if (cursor != null) {
      var decoded = PageCursor.decode(cursor);
      try {
        afterType = ContactType.valueOf(decoded.key());
      } catch (IllegalArgumentException e) {
        throw new ServiceException("続きの位置（cursor）が不正です");
      }
      afterValue = PageCursor.decodeKey(decoded.id());
    }
    var page =
        CursorPage.of(
            candidateRepository.groups(search, type, afterType, afterValue, size + 1),
            size,
            group -> groupCursor(group.type(), group.value()));
    var matches =
        candidateRepository.members(
            page.content().stream()
                .filter(group -> group.total() <= MAX_LISTED_GROUP_SIZE)
                .toList());
    var rows =
        matches.stream()
            .collect(
                Collectors.groupingBy(
                    match -> groupCursor(match.type(), match.value()),
                    LinkedHashMap::new,
                    Collectors.mapping(
                        CustomerCandidateRepository.Match::customer, Collectors.toList())));
    var customers =
        matches.stream().map(CustomerCandidateRepository.Match::customer).distinct().toList();
    var material = fetchComparisonMaterial(customers.stream().map(Customer::getId).toList());
    var comparisons =
        toComparisonRows(customers, material).stream()
            .collect(Collectors.toMap(CustomerMergeComparisonResponse::id, Function.identity()));
    return page.map(
        group ->
            new CustomerDuplicateGroupResponse(
                group.type(),
                group.value(),
                group.total(),
                rows.getOrDefault(groupCursor(group.type(), group.value()), List.of()).stream()
                    .map(customer -> comparisons.get(customer.getId()))
                    .toList()));
  }

  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CursorPage<CustomerMergeComparisonResponse> duplicateCustomers(
      ContactType type, String value, String cursor, int requestedSize) {
    if (value == null || value.isBlank() || value.length() > 320)
      throw new ServiceException("320文字以内の連絡先を指定してください");
    String normalized =
        switch (type) {
          case PHONE -> ContactValues.phone(value, "value");
          case EMAIL -> ContactValues.email(value, "value");
          case LINE -> value.strip();
        };
    int size = candidatePageSize(requestedSize);
    String afterId = cursor == null ? "" : PageCursor.decodeKey(cursor);
    if (!afterId.matches("[0-9]*")) throw new ServiceException("続きの位置（cursor）が不正です");
    var page =
        CursorPage.of(
            candidateRepository.members(type, normalized, afterId, size + 1),
            size,
            customer -> PageCursor.encodeKey(customer.getId()));
    return new CursorPage<>(
        toComparisonRows(
            page.content(),
            fetchComparisonMaterial(page.content().stream().map(Customer::getId).toList())),
        page.nextCursor());
  }

  private static int candidatePageSize(int size) {
    if (size < 1 || size > CursorPage.MAX_SIZE) throw new ServiceException("取得件数は1〜2000件で指定してください");
    return size;
  }

  private static String groupCursor(ContactType type, String value) {
    return new PageCursor(type.name(), PageCursor.encodeKey(value)).encode();
  }

  /**
   * 顧客一覧から選んだ任意の 2 行を、統合の前に見比べるための読み口。重複候補に出てこない行どうしでも引ける。
   *
   * <p>顧客詳細では代わりにならない。あちらは旧 ID を統合先へ解決する（{@link #get}）ので、選んだ片方が既に墓標なら同じ 1 行が 2 列に並び、同一人物かを答える画面が
   * 見比べる対象を静かに失う。
   *
   * <p>行・紐づけ・受注件数の 3 段は 1 つの断面で読む（{@code REPEATABLE
   * READ}）。取り消せない操作の判断材料が、片方はもう墓標という別の世界の値で並ばないようにする。
   *
   * @param customerIds 見比べる 2 行。並びはそのまま応答の並びになる
   */
  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<CustomerMergeComparisonResponse> mergeComparison(List<String> customerIds) {
    if (customerIds.size() != COMPARISON_SIZE) {
      throw new ServiceException("見比べる顧客を 2 件指定してください");
    }
    if (customerIds.get(0).equals(customerIds.get(1))) {
      throw new ServiceException("同じ顧客どうしは見比べられません");
    }
    Map<String, Customer> rows =
        customerRepository.findByIdInAndMergedIntoIdIsNull(customerIds).stream()
            .collect(Collectors.toMap(Customer::getId, Function.identity()));
    // 墓標・他店舗・不存在はどれもここに落ちる。区別して案内すると、他店舗の顧客の存在が漏れる
    if (rows.size() != COMPARISON_SIZE) {
      throw new NotFoundException("顧客が見つかりません");
    }
    // 並びは要求のまま返す。左右が入れ替わると、画面で選んだ「残す行」が別人を指しうる
    return toComparisonRows(
        customerIds.stream().map(rows::get).toList(), fetchComparisonMaterial(customerIds));
  }

  private List<CustomerMergeComparisonResponse> toComparisonRows(
      List<Customer> customers, ComparisonMaterial material) {
    var preferred =
        customerContactService.preferred(customers.stream().map(Customer::getId).toList());
    return customers.stream()
        .map(
            customer ->
                customerMapper.toComparisonResponse(
                    customer,
                    material.linked(customer.getId()),
                    material.orderCount(customer.getId()),
                    preferred.getOrDefault(customer.getId(), List.of())))
        .toList();
  }

  /**
   * 見比べる材料（会員紐づけの有無・受注件数）を引く。どちらも顧客行が持たない事実で、候補ぜんたい・見比べる 2 行の どちらでも渡された ID をまとめて 1
   * 回ずつ引く（行ごとの追加問い合わせを作らない）。
   */
  private ComparisonMaterial fetchComparisonMaterial(List<String> customerIds) {
    if (customerIds.isEmpty()) {
      return new ComparisonMaterial(Set.of(), Map.of());
    }
    return new ComparisonMaterial(
        customerMemberLinkRepository
            .findByCustomerIdInAndStatus(customerIds, LinkStatus.ACTIVE)
            .stream()
            .map(CustomerMemberLink::getCustomerId)
            .collect(Collectors.toSet()),
        customerMergeRepository.countOrdersByCustomerId(customerIds).stream()
            .collect(
                Collectors.toMap(
                    CustomerOrderCountView::getCustomerId, CustomerOrderCountView::getOrderCount)));
  }

  /** 受注を 1 件も持たない顧客は受注件数の結果に現れないため、既定は 0 件。 */
  private record ComparisonMaterial(Set<String> linkedIds, Map<String, Long> orderCounts) {

    boolean linked(String customerId) {
      return linkedIds.contains(customerId);
    }

    long orderCount(String customerId) {
      return orderCounts.getOrDefault(customerId, 0L);
    }
  }

  /** 名前と全有効連絡先を検索する。省略された条件は SQL へ渡さず、可変の述語として組み立てる。 */
  private static Specification<Customer> searchSpec(String search, String classification) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      // 墓標は台帳に並ばない。「統合済みも表示」の切替は設けない — 墓標の存在を知る必要があるのは
      // 旧 ID の解決と統合履歴の閲覧だけである（ADR 0010）。
      predicates.add(cb.isNull(root.get("mergedIntoId")));
      if (search != null) {
        char escape = LIKE_ESCAPE.getEscapeCharacter();
        String pattern = "%" + LIKE_ESCAPE.escape(search.toLowerCase(Locale.ROOT)) + "%";
        var contactQuery = query.subquery(String.class);
        var contact = contactQuery.from(CustomerContact.class);
        contactQuery
            .select(contact.get("customerId"))
            .where(
                cb.equal(contact.get("customerId"), root.get("id")),
                cb.equal(contact.get("storeId"), root.get("storeId")),
                cb.isFalse(contact.get("deleted")),
                CustomerContactSearch.matches(contact, cb, search));
        predicates.add(
            cb.or(cb.like(cb.lower(root.get("name")), pattern, escape), cb.exists(contactQuery)));
      }
      if (classification != null) {
        predicates.add(cb.equal(root.get("classification"), classification));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * 顧客詳細。旧 ID を渡されたら統合先の行を返し、統合済みであることと元の ID を応答に載せる。
   *
   * <p>3xx リダイレクトは採らない — HTTP クライアントが透過的に追随するため、画面が統合の発生そのものを知れなくなる。
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public CustomerResponse get(String id) {
    Customer customer =
        customerRepository
            .findResolvingMerge(id)
            .orElseThrow(() -> new NotFoundException("顧客が見つかりません"));
    CustomerResponse response = customerMapper.toResponse(customer);
    response.setPreferredContacts(
        customerContactService
            .preferred(List.of(customer.getId()))
            .getOrDefault(customer.getId(), List.of()));
    withMemberLink(response, activeMemberCodeOf(customer.getId()));
    return withMergeMark(response, id, customer.getId());
  }

  @StoreScoped
  @Transactional
  public CustomerResponse create(CustomerCreateRequest request) {
    // store_id は StoreScopeStampListener が @PrePersist で採番する
    Customer customer = customerMapper.toEntity(request);
    // 作成直後の顧客は定義上まだ会員と紐づいていない
    customerRepository.saveAndFlush(customer);
    for (var contact : request.getContacts())
      customerContactService.create(customer.getId(), contact);
    var response = customerMapper.toResponse(customer);
    response.setPreferredContacts(List.of());
    return withMemberLink(response, null);
  }

  @StoreScoped
  @Transactional
  public CustomerResponse update(String id, CustomerUpdateRequest request) {
    Customer customer =
        customerRepository.findById(id).orElseThrow(() -> new NotFoundException("顧客が見つかりません"));
    if (customerRepository.isMerged(id)) {
      throw new ConflictException(MERGED_CUSTOMER_NOT_EDITABLE);
    }

    customer.apply(customerMapper.toPatch(request));

    var response = customerMapper.toResponse(customerRepository.save(customer));
    response.setPreferredContacts(
        customerContactService.preferred(List.of(id)).getOrDefault(id, List.of()));
    return withMemberLink(response, activeMemberCodeOf(id));
  }

  /**
   * 顧客を削除する。統合に関与した行は存続行・被統合行のいずれも削除できない — 統合履歴が誤統合の唯一の修復根拠であり、旧 ID の解決も
   * 墓標が残っていて初めて届くため、誤削除で消えるほうが重い（ADR 0010）。
   */
  @StoreScoped
  @Transactional
  public void delete(String id) {
    if (customerRepository.findByIdForUpdate(id).isEmpty()) {
      throw new NotFoundException("顧客が見つかりません");
    }
    // 墓標も「統合に関与した行」なので下の判定でも撥ねられるが、次の一手が違う — 墓標を消したい人が
    // 求めているのは統合先の編集である。先に判定して案内を分ける。
    if (customerRepository.isMerged(id)) {
      throw new ConflictException(MERGED_CUSTOMER_NOT_EDITABLE);
    }
    if (customerContactRepository.existsByCustomerIdOrOriginCustomerId(id, id)) {
      throw new ConflictException("連絡先の履歴がある顧客は削除できません");
    }
    if (customerMergeRepository.existsInvolving(id)) {
      throw new ConflictException(MERGED_CUSTOMER_UNDELETABLE);
    }
    try {
      customerRepository.deleteById(id);
      // DELETE を今この場へ流す。トランザクション境界の commit まで遅れると、外部キー違反が
      // この catch を素通りして全域ハンドラの兜底（500）へ落ちる。
      customerRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      // 統合の側は事前判定と削除の間に統合が確定した競合の最終防波堤。統合に関与した行は履歴の 2 本と、
      // 存続行なら墓標からの自己参照にも指されており、どれが先に違反として現れるかは DB の検査順に依るので
      // 3 本とも同じ案内へ写す。受注の側は事前判定を持たない — 数えること自体は HQL の中で order を
      // 名指せば可能だが、判定と DELETE の間に受注が生まれれば覆るため、この写像が唯一の分類になる。
      // 写像を持たない整合性違反は実装欠陥として大きく失敗させる。
      Supplier<RuntimeException> undeletable =
          () -> new ConflictException(MERGED_CUSTOMER_UNDELETABLE);
      throw IntegrityViolations.translate(
          ex,
          Map.of(
              DbConstraint.FK_T_CUSTOMER_CONTACTS_CUSTOMER,
                  () -> new ConflictException("連絡先の履歴がある顧客は削除できません"),
              DbConstraint.FK_T_CUSTOMER_CONTACTS_ORIGIN,
                  () -> new ConflictException("連絡先の履歴がある顧客は削除できません"),
              DbConstraint.FK_T_CUSTOMER_CONTACT_HISTORY_ORIGIN,
                  () -> new ConflictException("連絡先の履歴がある顧客は削除できません"),
              DbConstraint.FK_T_CUSTOMER_MERGES_SURVIVING, undeletable,
              DbConstraint.FK_T_CUSTOMER_MERGES_MERGED, undeletable,
              DbConstraint.FK_T_CUSTOMERS_MERGED_INTO, undeletable,
              DbConstraint.FK_T_ORDERS_CUSTOMER_ALIVE,
                  () -> new ConflictException(ORDERED_CUSTOMER_UNDELETABLE)));
    }
  }

  private String activeMemberCodeOf(String customerId) {
    return customerMemberLinkRepository
        .findByCustomerIdAndStatus(customerId, LinkStatus.ACTIVE)
        .map(CustomerMemberLink::getMemberCode)
        .orElse(null);
  }

  /** 旧 ID で引かれたときだけ統合の標識を載せる。生きた行を引いた応答には欄そのものが現れない（non_null 直列化）ので、 欄の有無が「この ID は統合済みか」の答えになる。 */
  private static CustomerResponse withMergeMark(
      CustomerResponse response, String requestedId, String targetId) {
    if (requestedId.equals(targetId)) {
      return response;
    }
    response.setMerged(true);
    response.setMergedFromId(requestedId);
    return response;
  }

  /** 会員紐づけの投影を載せる。memberLinked は関連状態の有無そのものなので、常に真偽値が入る。 */
  private static CustomerResponse withMemberLink(CustomerResponse response, String memberCode) {
    response.setMemberLinked(memberCode != null);
    response.setLinkedMemberCode(memberCode);
    return response;
  }
}
