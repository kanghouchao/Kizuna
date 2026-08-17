package com.kizuna.customer.application;

import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerDuplicateGroupResponse;
import com.kizuna.customer.api.dto.CustomerMapper;
import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerSummaryResponse;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerDuplicateGroupView;
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
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  /**
   * 1 つのグループについて返す行数の上限。総数は {@code total} が別に持つので、切っても件数は偽らない。
   *
   * <p>番号の字面をいくら絞り込んでも、識別の手がかりを持たない値（移行データの代替値 {@code 0000000000} 等）は必ずすり抜ける。
   * 値の形ではなく<b>グループの大きさ</b>で頭打ちにすると、すり抜けた値がどんな字面でも取得と描画が有界になる。
   */
  private static final int MAX_ROWS_PER_GROUP = 20;

  private static final String MERGED_CUSTOMER_UNDELETABLE =
      "統合に関与した顧客は削除できません。統合履歴と旧 ID の解決の根拠になります";

  private static final String ORDERED_CUSTOMER_UNDELETABLE = "受注が紐づいている顧客は削除できません。来店の記録が参照しています";

  /**
   * 墓標そのものを名指した書き換えの案内。更新・削除・ポイント調整・解除の 4 経路が同じ文面を返すのは、どれも次の一手が同じ
   * （統合先の行を編集する）だからである。統合先へ黙って向け直してよいのは参照の書き込み先だけで、名指した行への書き換えは撥ねる（ADR 0010）。
   */
  private static final String MERGED_CUSTOMER_NOT_EDITABLE = "統合済みの顧客です。統合先の顧客を編集してください";

  private final CustomerRepository customerRepository;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final CustomerMergeRepository customerMergeRepository;
  private final CustomerMapper customerMapper;

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<CustomerSummaryResponse> list(
      String search, String rank, String classification, Pageable pageable) {
    Specification<Customer> spec = searchSpec(search, rank, classification);
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
    return page.map(
        customer -> {
          CustomerSummaryResponse row = customerMapper.toSummaryResponse(customer);
          row.setMemberLinked(activeCodes.containsKey(customer.getId()));
          return row;
        });
  }

  /**
   * 重複候補 — 同店の生きた行のうち、第一電話番号が一致する 2 行以上のグループ。
   *
   * <p>提示は手がかりであって判定ではない。確度スコアも自動統合も持たず、どれを畳むかは常に人が決める（ADR 0010）。 同じ番号の別人（連絡先を共有する同伴者）は正規に起こりうる。
   *
   * <p>受注件数と紐づけの有無を添えるのは、別人を誤って畳まないための材料だからである。無いと人手の確認が形だけになる。
   *
   * <p><b>4 段は 1 つの断面で読む</b>（{@code REPEATABLE READ}）。見出し・行・紐づけ・受注件数と 4 回問い合わせるので、既定の READ
   * COMMITTED では文ごとに断面を取り直し、間に他者の commit が挟まると同じ応答の中で違う世界を見る — 件数 {@code total} だけが古いまま行が 1
   * つ増え、上限に収まると数えたグループが上限を超えて返る。変種ごとに手当てせず断面を固定して根を断つのは {@code OrderService} の群読み口と同じ選択で、機構そのものは
   * {@code OrderGroupReadSnapshotIT} が実 PostgreSQL で見ている。
   */
  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CursorPage<CustomerDuplicateGroupResponse> listDuplicateCandidates(
      String cursor, int requestedSize) {
    int size = CursorPage.clampSize(requestedSize);
    // 続きの有無は上限より 1 件多く取って判る。総件数の問い合わせを毎回撒かずに済む。
    Limit limit = Limit.of(size + 1);
    CursorPage<CustomerDuplicateGroupView> page =
        CursorPage.of(
            cursor == null
                ? customerRepository.findDuplicatePhoneNumbers(limit)
                : customerRepository.findDuplicatePhoneNumbersAfter(
                    PageCursor.decodeKey(cursor), limit),
            size,
            group -> PageCursor.encodeKey(group.getPhoneNumber()));

    Map<String, List<Customer>> rowsByPhoneNumber = fetchRows(page.content());
    List<String> ids =
        rowsByPhoneNumber.values().stream().flatMap(List::stream).map(Customer::getId).toList();
    // 紐づけと受注件数は候補全体をまとめて 1 回ずつで引く（行ごとの追加問い合わせを作らない）。
    Set<String> linkedIds =
        ids.isEmpty()
            ? Set.of()
            : customerMemberLinkRepository
                .findByCustomerIdInAndStatus(ids, LinkStatus.ACTIVE)
                .stream()
                .map(CustomerMemberLink::getCustomerId)
                .collect(Collectors.toSet());
    Map<String, Long> orderCounts =
        ids.isEmpty()
            ? Map.of()
            : customerMergeRepository.countOrdersByCustomerId(ids).stream()
                .collect(
                    Collectors.toMap(
                        CustomerOrderCountView::getCustomerId,
                        CustomerOrderCountView::getOrderCount));

    return new CursorPage<>(
        page.content().stream()
            .map(
                group ->
                    new CustomerDuplicateGroupResponse(
                        group.getPhoneNumber(),
                        group.getTotal(),
                        rowsByPhoneNumber.getOrDefault(group.getPhoneNumber(), List.of()).stream()
                            .map(
                                customer ->
                                    customerMapper.toDuplicateResponse(
                                        customer,
                                        linkedIds.contains(customer.getId()),
                                        orderCounts.getOrDefault(customer.getId(), 0L)))
                            .toList()))
            // 候補を数えてから引き直すまでの間に統合が確定すると、片方が墓標になって 1 行だけの
            // グループが残る。1 行は重複ではないので、候補として出さない
            .filter(group -> group.customers().size() >= 2)
            .toList(),
        // 続きの位置は電話番号から決まるので、上で落ちたグループがあっても付け替えない
        page.nextCursor());
  }

  /**
   * グループごとの行を引く。上限に収まるグループはまとめて 1 回で、桁外れのグループだけを 1 本ずつ上限つきで引く。
   *
   * <p>まとめて引く側に大きいグループを混ぜないのは、1 つのグループが取得を食い潰さないため。桁外れのグループは構造上まれ
   * （移行データの代替値のような、識別の手がかりを持たない番号）なので、追加の問い合わせは通常 0 本になる。
   */
  private Map<String, List<Customer>> fetchRows(List<CustomerDuplicateGroupView> groups) {
    List<String> boundedPhoneNumbers =
        groups.stream()
            .filter(group -> group.getTotal() <= MAX_ROWS_PER_GROUP)
            .map(CustomerDuplicateGroupView::getPhoneNumber)
            .toList();
    Map<String, List<Customer>> byPhoneNumber =
        boundedPhoneNumbers.isEmpty()
            ? new LinkedHashMap<>()
            : customerRepository
                .findByPhoneNumberInAndMergedIntoIdIsNullOrderByPhoneNumberAscIdAsc(
                    boundedPhoneNumbers)
                .stream()
                .collect(
                    Collectors.groupingBy(
                        Customer::getPhoneNumber, LinkedHashMap::new, Collectors.toList()));
    groups.stream()
        .filter(group -> group.getTotal() > MAX_ROWS_PER_GROUP)
        .forEach(
            group ->
                byPhoneNumber.put(
                    group.getPhoneNumber(),
                    customerRepository.findByPhoneNumberAndMergedIntoIdIsNullOrderByIdAsc(
                        group.getPhoneNumber(), Limit.of(MAX_ROWS_PER_GROUP))));
    return byPhoneNumber;
  }

  /**
   * 検索語は 名前・電話番号・LINE ID を横断し、rank / classification は完全一致の絞り込み。 null の条件は述語を生成しない（JPQL の ":param is
   * null or ..." パターンは PostgreSQL の null パラメータ型推論で 500 になるため Specification で組み立てる）。
   */
  private static Specification<Customer> searchSpec(
      String search, String rank, String classification) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      // 墓標は台帳に並ばない。「統合済みも表示」の切替は設けない — 墓標の存在を知る必要があるのは
      // 旧 ID の解決と統合履歴の閲覧だけである（ADR 0010）。
      predicates.add(cb.isNull(root.get("mergedIntoId")));
      if (search != null) {
        char escape = LIKE_ESCAPE.getEscapeCharacter();
        String pattern = "%" + LIKE_ESCAPE.escape(search.toLowerCase()) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern, escape),
                cb.like(root.get("phoneNumber"), "%" + LIKE_ESCAPE.escape(search) + "%", escape),
                cb.like(cb.lower(root.get("lineId")), pattern, escape)));
      }
      if (rank != null) {
        predicates.add(cb.equal(root.get("rank"), rank));
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
    withMemberLink(response, activeMemberCodeOf(customer.getId()));
    return withMergeMark(response, id, customer.getId());
  }

  @StoreScoped
  @Transactional
  public CustomerResponse create(CustomerCreateRequest request) {
    // store_id は StoreScopeStampListener が @PrePersist で採番する
    Customer customer = customerMapper.toEntity(request);
    // 作成直後の顧客は定義上まだ会員と紐づいていない
    return withMemberLink(customerMapper.toResponse(customerRepository.save(customer)), null);
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

    return withMemberLink(
        customerMapper.toResponse(customerRepository.save(customer)), activeMemberCodeOf(id));
  }

  /**
   * 顧客を削除する。統合に関与した行は存続行・被統合行のいずれも削除できない — 統合履歴が誤統合の唯一の修復根拠であり、旧 ID の解決も
   * 墓標が残っていて初めて届くため、誤削除で消えるほうが重い（ADR 0010）。
   */
  @StoreScoped
  @Transactional
  public void delete(String id) {
    if (!customerRepository.existsById(id)) {
      throw new NotFoundException("顧客が見つかりません");
    }
    // 墓標も「統合に関与した行」なので下の判定でも撥ねられるが、次の一手が違う — 墓標を消したい人が
    // 求めているのは統合先の編集である。先に判定して案内を分ける。
    if (customerRepository.isMerged(id)) {
      throw new ConflictException(MERGED_CUSTOMER_NOT_EDITABLE);
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
