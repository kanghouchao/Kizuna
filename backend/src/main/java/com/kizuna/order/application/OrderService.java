package com.kizuna.order.application;

import com.kizuna.customer.application.CustomerReferenceResolver;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.order.api.dto.OrderApplicationConfirmationRequest;
import com.kizuna.order.api.dto.OrderApplicationDeclineRequest;
import com.kizuna.order.api.dto.OrderApplicationResponse;
import com.kizuna.order.api.dto.OrderArchiveResponse;
import com.kizuna.order.api.dto.OrderCancellationRequest;
import com.kizuna.order.api.dto.OrderCastCandidateResponse;
import com.kizuna.order.api.dto.OrderCompletionPreviewResponse;
import com.kizuna.order.api.dto.OrderCompletionRequest;
import com.kizuna.order.api.dto.OrderCompletionResponse;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderReceptionistResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderSummaryResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.api.dto.OrderWorkQueueResponse;
import com.kizuna.order.domain.IllegalOrderStateTransitionException;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.order.domain.OrderApplicationView;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderPatch;
import com.kizuna.order.domain.OrderQueryCriteria;
import com.kizuna.order.domain.OrderReceiptToken;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.OrderView;
import com.kizuna.order.domain.ReceptionRoute;
import com.kizuna.order.infrastructure.OrderSearchQuery;
import com.kizuna.order.infrastructure.OrderSearchQuery.OrderedRow;
import com.kizuna.order.infrastructure.ReceiptTokenGenerator;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.shift.application.ConfirmedShiftLookupService;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

  /** 指名が成立しないことを店舗スタッフへ返すときの文言。列挙を防ぐ 404 ではなく理由と対処の分かる 400 で返す。 */
  private static final String NOT_NOMINATABLE_MESSAGE = "指名できるキャストではありません。在籍中のキャストを選んでください";

  /** 台帳行を起こすときの顧客ランク。DB 既定（{@code SILVER}）と同義で、{@code OrderMapper#toCustomer} と揃える。 */
  private static final String DEFAULT_RANK = "SILVER";

  private final OrderRepository orderRepository;
  private final OrderApplicationRepository orderApplicationRepository;
  private final OrderSearchQuery orderSearchQuery;
  private final OrderAttributionRepository orderAttributionRepository;
  private final OrderReceiptTokenRepository orderReceiptTokenRepository;
  private final ReceiptTokenGenerator receiptTokenGenerator;
  private final CustomerRepository customerRepository;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final CustomerReferenceResolver customerReferenceResolver;
  private final NominatableCastLookup nominatableCast;
  private final ConfirmedShiftLookupService confirmedShiftLookupService;
  private final PointLedgerService pointLedgerService;
  private final PlatformUserRepository platformUserRepository;
  private final RoleRepository roleRepository;
  private final StoreContext storeContext;
  private final BusinessDateService businessDateService;
  private final OrderMapper orderMapper;

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<OrderSummaryResponse> list(String customerId, Pageable pageable) {
    // 一覧は集約を経由せず JPQL join projection で取得。customerId は顧客詳細の注文履歴用
    String filter = (customerId == null || customerId.isBlank()) ? null : customerId;
    return orderRepository.findAllViews(filter, pageable).map(orderMapper::toSummaryResponse);
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
   * com.kizuna.order.infrastructure.OrderSearchQuery} にその理由を記す）。
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
  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CursorPage<OrderWorkQueueResponse> listWorkQueue(
      OrderQueryCriteria criteria, String cursor, int requestedSize) {
    int size = CursorPage.clampSize(requestedSize);
    // 続きの有無は上限より 1 件多く取って判る。総件数の問い合わせを毎回撒かずに済む。
    List<OrderedRow> rows =
        orderSearchQuery.findRows(
            criteria, cursor == null ? null : PageCursor.decode(cursor), size + 1);
    // 続きの位置は並びを決めたこの問い合わせが返した鍵から組む。行の中身を引く 2 本目から組むと、
    // 2 本が別の断面を見た場合に並べたときの値と食い違い、間の受注を丸ごと飛ばす（OrderedRow に理由を記す）。
    Map<String, String> cursorsById =
        rows.stream()
            .collect(Collectors.toMap(OrderedRow::id, row -> String.valueOf(row.sortKey())));
    List<OrderView> views = viewsInOrder(rows.stream().map(OrderedRow::id).toList());
    return CursorPage.of(views, size, view -> cursorOf(view, cursorsById))
        .map(orderMapper::toWorkQueueResponse);
  }

  /**
   * アーカイブ（完了・取消）の読み口。作業キューと同じ検索・並び替えを、オフセットのページャで辿る。
   *
   * <p>位置をページ番号で指せるのは、終端状態の受注が処理で消えず増えるだけだからである。
   *
   * <p>ここも 3 本（並び・本体・総件数）を 1 つの断面で読む（{@link #listWorkQueue} と同じ理由）。件数だけが別の断面から来ると、
   * 表示中の行数とページャの主張する総数が食い違う。
   */
  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<OrderArchiveResponse> listArchive(OrderQueryCriteria criteria, Pageable pageable) {
    // アーカイブは位置をページ番号で指すため、鍵の値そのものは要らない
    List<String> ids = orderSearchQuery.findIds(criteria, pageable);
    List<OrderArchiveResponse> rows =
        viewsInOrder(ids).stream().map(orderMapper::toArchiveResponse).toList();
    return new PageImpl<>(rows, pageable, orderSearchQuery.count(criteria));
  }

  /**
   * 名指された ID の読み側 projection を、渡された ID の並びのまま返す。
   *
   * <p>1 件でも引けなければ<b>失敗させる</b>。黙って取り落とすと、作業キューでは「上限より 1 件多く取れたか」で判定している 続きの有無がその 1
   * 件ぶん狂い、続きがあるのに「もう無い」と返る — 呼出側はそこで読むのをやめるので、 対応が要る受注が画面から永久に消える。件数の減った 1
   * ページとして現れるため、断らなければ表示上は正常に見えてしまう。
   *
   * <p>2 つの問い合わせは同じ店舗の文脈・同じトランザクションで走るので、母集合が食い違うこと自体が実装の欠陥である （典型は動的な問い合わせの側に店舗行分離が効いていない場合）。
   *
   * <p>捕まえるのは<b>行が消えたこと</b>だけで、行の値が変わったことは見ない。READ COMMITTED では 2 本が別の断面を
   * 見るため値の食い違い自体は起こりうるが、続きの位置は並びを決めた 1 本目の鍵から組むので（{@code OrderSearchQuery.OrderedRow}）、表示が 1
   * 拍古いことしか意味しない。
   */
  private List<OrderView> viewsInOrder(List<String> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<String, OrderView> byId =
        orderRepository.findViewsByIds(ids).stream()
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

  @StoreScoped
  @Transactional(readOnly = true)
  public OrderResponse get(String id) {
    return toResponse(
        orderRepository.findById(id).orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id)));
  }

  /**
   * 店舗・HQ が起こす受注を作成する。この経路の受注は<b>確定で出生する</b>（出生状態は {@link OrderMapper#toEntity} が持つ）—
   * 電話口で受けると決めた時点で 可否は判断済みであり、画面上でもう一度確定し直す段は無い。
   *
   * @param actorEmail 実行者。受付担当が省略されたときの補完先になる
   */
  @StoreScoped
  @Transactional
  public OrderResponse create(OrderCreateRequest request, String actorEmail) {
    // Web 申請の経路（MEMBER_WEB / GUEST_WEB）は申請の確定だけが書く値。台帳を触るより先に撥ねる —
    // 広告費と効果集計の根拠になる記録が代理入力で偽装されると、後から申請と手入力を切り分ける手立てが無い。
    if (request.getReceptionRoute() != null && !request.getReceptionRoute().isStoreSelectable()) {
      throw new ServiceException("受付経路に Web 申請は指定できません。予約申請の確定だけが MEMBER_WEB / GUEST_WEB を名乗ります");
    }

    // MapStructを使用して基本的なフィールドをマッピング（store_id は StoreScopeStampListener が @PrePersist で採番）
    Order order = orderMapper.toEntity(request);

    // 内訳は集約に差し替えさせる。合計は行の総和として集約が導出するので、作成の契約は合計を受け取らない。
    if (request.getFeeLines() != null) {
      order.replaceStoreFeeLines(orderMapper.toFeeLineDrafts(request.getFeeLines()));
    }

    // 複雑な関連ロジックの処理（顧客のスマートリンク）
    handleCustomerLinking(request, order);

    // 指名は候補一覧と同じ条件で書き込み側でも見る — 候補に出さないだけでは、キャスト ID を直接送る要求を防げない。
    // 店舗が起こす受注は常に新しい指名を立てるため、据え置きの余地は無く無条件に要求する。
    nominatableCast
        .find(storeContext.getStoreId(), request.getCastId())
        .orElseThrow(() -> new ServiceException(NOT_NOMINATABLE_MESSAGE));
    order.assignCast(request.getCastId());
    order.assignReceptionist(resolveReceptionist(request.getReceptionistId(), actorEmail));

    Order saved = orderRepository.save(order);
    return toResponse(saved);
  }

  /**
   * 作成時の受付担当を決める。明示された ID はそのまま検証し、省略されたときは実行者本人を確定操作と同じ適格述語（{@link #eligibleReceptionistId}）で解決する。
   *
   * <p>適格でない実行者（店舗を授権する HQ 管理者など）では黙って未設定にせず撥ねる。確定操作が未設定のまま残すのは
   * 会員の申請が既に成立しているからで、こちらは受注そのものをこれから起こす — 誤りは早いほうが直せる。
   */
  private Long resolveReceptionist(Long requested, String actorEmail) {
    if (requested != null) {
      validateReceptionist(requested);
      return requested;
    }
    return eligibleReceptionistId(actorEmail)
        .orElseThrow(() -> new ServiceException("受付担当を指定してください"));
  }

  /**
   * 受注の内容を部分更新する。状態は動かさない — 完了は完了処理が、取消は取消操作が独占する（ADR 0013）。
   *
   * <p>終端状態（完了・取消）の受注はこの口では書き換えられない。受注には変更履歴が無いため、ここを開けておくことは 「誰が・いつ・何を」のどれも残さずに確定した記録を動かす裏口になる。
   */
  @StoreScoped
  @Transactional
  public OrderWorkQueueResponse update(String id, OrderUpdateRequest request) {
    Order order =
        orderRepository.findById(id).orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));

    // 判定は「終端か」の単一述語で行い、状態ごとに書き分けない。書き分けは同じ規則を二箇所に持たせ、
    // 片方だけが更新される入口になる（ADR 0013）。完了後の内容訂正は権限付き・記録付きの別経路が担う。
    if (order.getStatus() != null && order.getStatus().isTerminal()) {
      throw new ServiceException("完了・取消済みの受注は編集できません");
    }

    // 空文字は「送っていない」と同じに扱う。編集画面の未選択がそのまま乗ってくる形なので、存在しない
    // キャストとして扱って 404 を返すより、指名なしの要求として同じ判定に載せる方が呼び手に意味が通る。
    String castId =
        (request.getCastId() == null || request.getCastId().isBlank()) ? null : request.getCastId();
    Long receptionistId = request.getReceptionistId();

    // 指名・受付担当の検証は書き換えより先に済ませる。撥ねる要求が集約を触った後だと、拒否の健全さが
    // トランザクションの巻き戻しだけに掛かる（同一トランザクション内の後続の読みには変わった値が見えてしまう）。
    //
    // 必須性は契約ではなく受注の状態が決める。未設定のまま確定した受注（指名を外した
    // 会員申請、受付候補でない実行者が確定したもの）は未設定のまま他項目を直せなければならない一方、
    // 既に付いているものを省略で外せると、この汎用更新が指名解除・受付担当解除の裏口になる。
    if (receptionistId != null) {
      // 指名と同じく、縛るのは差し替えだけで据え置き（同じ受付担当の再送）は素通しする。この経路は
      // 受付担当の付いた受注に receptionist_id の再送を必須にしているため、無条件に適格を要求すると、
      // 受付担当が退職・権限剥奪・他店異動で適格でなくなった受注が備考・人数の修正もできなくなる。
      if (!receptionistId.equals(order.getReceptionistId())) {
        validateReceptionist(receptionistId);
      }
    } else if (order.getReceptionistId() != null) {
      throw new ServiceException("受付担当を外すことはできません。受付担当を指定してください");
    }
    if (castId != null) {
      // 縛るのは新しく立てる指名と差し替えだけで、据え置き（同じ指名の再送）は素通しする。この経路は指名済みの
      // 受注に cast_id の再送を必須にしているため、無条件に在籍中を要求すると、指名者が在籍停止になった確定済みの
      // 受注が備考・人数の修正も完了への遷移もできなくなる。据え置かれた指名は成立した時点で検証済みで、
      // cast_id には FK も掛かっているので、素通しが存在しないキャストを通すことにはならない。
      if (!castId.equals(order.getCastId())) {
        nominatableCast
            .find(storeContext.getStoreId(), castId)
            .orElseThrow(() -> new ServiceException(NOT_NOMINATABLE_MESSAGE));
      }
    } else if (order.getCastId() != null) {
      throw new ServiceException("指名を外すことはできません。キャストを指定してください");
    }

    // 連絡先の訂正も書き換えより先に判定させる。顧客が着いた受注では集約が撥ねる（黙って捨てない）。
    // 送られなかった要求で呼ばないのは、顧客が着いた受注の他項目の編集まで巻き添えで撥ねないため。
    if (request.getContactName() != null || request.getContactPhoneNumber() != null) {
      order.correctContact(request.getContactName(), request.getContactPhoneNumber());
    }

    // 非nullフィールドのみをドメインの部分更新コマンドとして適用
    order.apply(orderMapper.toPatch(request));

    // 関連 ID の更新（存在確認は上で済ませている）
    if (castId != null) {
      order.assignCast(castId);
    }
    if (receptionistId != null) {
      order.assignReceptionist(receptionistId);
    }

    Order saved = orderRepository.save(order);
    return toWorkQueueResponse(saved.getId());
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
  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<OrderApplicationResponse> listApplications(
      Set<OrderApplicationStatus> statuses, String cursor, int requestedSize) {
    int size = CursorPage.clampSize(requestedSize);
    Limit limit = Limit.of(size + 1);
    List<OrderApplicationView> fetched =
        cursor == null
            ? orderApplicationRepository.findViews(statuses, limit)
            : fetchApplicationsAfter(statuses, PageCursor.decode(cursor), limit);
    LocalDate today = businessDateService.currentBusinessDate();
    return CursorPage.of(fetched, size, OrderService::applicationCursorOf)
        .map(view -> toApplicationResponse(view, today));
  }

  private List<OrderApplicationView> fetchApplicationsAfter(
      Set<OrderApplicationStatus> statuses, PageCursor cursor, Limit limit) {
    return orderApplicationRepository.findViewsAfter(
        statuses, cursor.dateKey(), cursor.id(), limit);
  }

  private static String applicationCursorOf(OrderApplicationView view) {
    return new PageCursor(view.getBusinessDate().toString(), view.getId()).encode();
  }

  private OrderApplicationResponse toApplicationResponse(
      OrderApplicationView view, LocalDate currentBusinessDate) {
    return OrderApplicationResponse.builder()
        .id(view.getId())
        .businessDate(view.getBusinessDate())
        .arrivalScheduledStartTime(view.getArrivalScheduledStartTime())
        .pax(view.getPax())
        .castId(view.getCastId())
        .castName(view.getCastName())
        .remarks(view.getRemarks())
        .status(view.getStatus() == null ? null : view.getStatus().name())
        .requesterMemberCode(view.getRequesterMemberCode())
        .requesterDeclaredName(view.getRequesterDeclaredName())
        .contactName(view.getContactName())
        .contactPhoneNumber(view.getContactPhoneNumber())
        .orderId(view.getOrderId())
        .declinedReason(view.getDeclinedReason())
        .expired(
            OrderApplication.isExpired(
                view.getStatus(), view.getBusinessDate(), currentBusinessDate))
        .build();
  }

  /**
   * 予約申請を確定する — 申請内容を予填した<b>受注の作成操作</b>。店舗が補完・調整した確定内容で Order を CONFIRMED で生成し、 申請行へ {@code
   * order_id} を回写する。申請原文は不変のまま残り、確定内容と対照できる（ADR 0017）。
   *
   * <p>指名は申請時と同じ在籍述語に加えて当日の確定シフトを要求する。申請から確定までの間にキャストの在籍停止や
   * 確定シフトの取り消しが起こりうるため、そのまま確定すると来店時に指名キャストがいない受注が成立してしまう。 対象は店舗スタッフなので、列挙を防ぐ 404 ではなく理由の分かる 400
   * で返す。
   *
   * <p>受付担当が未指定で、確定した本人が受付候補の条件を満たす場合はその本人を補う。条件を満たさない実行者 （店舗を授権する HQ
   * 管理者など）では未設定のまま残し、受付担当の適格条件を確定操作で迂回させない。
   *
   * <p>顧客は確定の時点で決め直す（{@link #ensureCustomerForRequester}）。完了時の会員解決は顧客の現在の関連だけを見る （ADR 0008
   * の一本道）ため、古い参照を持ち回ると申請者の来店とポイントが別会員へ積まれる。
   */
  @StoreScoped
  @Transactional
  public OrderResponse confirmApplication(
      String id, OrderApplicationConfirmationRequest request, String actorEmail) {
    OrderApplication application =
        orderApplicationRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("予約申請が見つかりません: " + id));

    // 検証は書き換えより先にすべて済ませる。撥ねる要求が集約や台帳を触った後だと、拒否の健全さが
    // トランザクションの巻き戻しだけに掛かる。
    LocalDate today = businessDateService.currentBusinessDate();
    application.ensureDecidable(today);
    if (request.getCastId() != null) {
      nominatableCast
          .find(storeContext.getStoreId(), request.getCastId())
          .orElseThrow(() -> new ServiceException("指名キャストが在籍中でないため確定できません。内容を修正するか謝絶してください"));
      if (!confirmedShiftLookupService.hasConfirmedShift(
          storeContext.getStoreId(), request.getCastId(), request.getBusinessDate())) {
        throw new ServiceException("指名キャストにこの日の確定シフトが無いため確定できません。内容を修正するか謝絶してください");
      }
    }
    if (request.getReceptionistId() != null) {
      validateReceptionist(request.getReceptionistId());
    }
    validateCustomerChoice(application, request);
    Long actorId = resolveActorId(actorEmail);

    Order order =
        Order.builder()
            .businessDate(request.getBusinessDate())
            .arrivalScheduledStartTime(request.getArrivalScheduledStartTime())
            .arrivalScheduledEndTime(request.getArrivalScheduledEndTime())
            .pax(request.getPax())
            .courseMinutes(request.getCourseMinutes())
            .remarks(request.getRemarks())
            .status(OrderStatus.CONFIRMED)
            // 受付経路は申請が入ってきた入口を写す。店舗の作成経路（create）はこの群を拒否する。
            // 判定を会員コードのスナップショットで行うのは、会員行の削除で requesterMemberId が
            // 欠落した会員申請をゲスト由来として記録しないため。
            .receptionRoute(
                application.isGuest() ? ReceptionRoute.GUEST_WEB : ReceptionRoute.MEMBER_WEB)
            .requesterMemberId(application.getRequesterMemberId())
            .requesterMemberCode(application.getRequesterMemberCode())
            .requesterDeclaredName(application.getRequesterDeclaredName())
            .build();
    if (request.getCastId() != null) {
      order.assignCast(request.getCastId());
    }
    if (request.getReceptionistId() != null) {
      order.assignReceptionist(request.getReceptionistId());
    } else {
      eligibleReceptionistId(actorEmail).ifPresent(order::assignReceptionist);
    }
    if (application.isGuest()) {
      linkCustomerChosenByStaff(order, application, request);
    } else if (application.getRequesterMemberId() != null) {
      // 申請者の会員 ID が欠落した申請（会員行の削除後）は整える先が無いため、顧客未設定のまま成立させる
      // （無帰属受注は正規の状態）。
      order.linkCustomer(ensureCustomerForRequester(application, actorEmail));
    }
    Order saved = orderRepository.save(order);
    application.confirmWith(saved.getId(), actorId, OffsetDateTime.now(), today);
    orderApplicationRepository.save(application);
    return toResponse(saved);
  }

  /**
   * 確定要求が名乗る顧客の選択を検める。書き換えより先に済ませる。
   *
   * <p>会員申請では受け付けない。会員の顧客は「今の関連」だけが決める一本道であり（ADR 0008）、店員が別の行を 選べると完了時のポイントが別会員へ積まれる。
   */
  private static void validateCustomerChoice(
      OrderApplication application, OrderApplicationConfirmationRequest request) {
    boolean choosesExisting = request.getCustomerId() != null && !request.getCustomerId().isBlank();
    boolean createsNew = request.getNewCustomer() != null;
    if (!application.isGuest() && (choosesExisting || createsNew)) {
      throw new ServiceException("会員の申請では顧客を選べません。顧客は会員の紐づけから決まります");
    }
    if (choosesExisting && createsNew) {
      throw new ServiceException("既存の顧客と新規作成のどちらか一方を選んでください");
    }
  }

  /**
   * ゲスト申請の受注が着く当店の顧客を、店員の判断のまま決める。既存の行を名指すか新しく起こすかの二択で、 どちらも無ければ顧客未設定のまま成立させ、申請の連絡先を受注側へ写す
   * （写さないと折返し先がどこにも残らない）。
   *
   * <p>電話番号での自動照合は行わない（ADR 0009）— 同店同号は正規に起こりうるうえ、一致行に会員関連付きの行があり得る以上、 機械が 1
   * 行を選ぶことが誤帰属・なりすましの入口になる。この判断の根拠を述べるのはここだけで、他の層は繰り返さない。
   */
  private void linkCustomerChosenByStaff(
      Order order, OrderApplication application, OrderApplicationConfirmationRequest request) {
    if (request.getCustomerId() != null && !request.getCustomerId().isBlank()) {
      // 既存の行へ着ける ID は顧客参照の解決口から得る。書く直前に対象の行を押さえ、
      // 取得できない顧客（不在・他店舗・墓標）はそこで 404 になる。
      order.linkCustomer(customerReferenceResolver.resolveForWrite(request.getCustomerId()));
      return;
    }
    if (request.getNewCustomer() != null) {
      // store_id は StoreScopeStampListener が @PrePersist で採番する。ランクは他の台帳行の作成経路と
      // 同じ既定を明示する — 列を写像している以上、省略は DB 既定ではなく null になる。
      // 起こしたばかりの行は他の経路の書き換えに晒されていないため、解決を経ずにそのまま着ける。
      Customer customer =
          customerRepository.save(
              Customer.builder()
                  .name(request.getNewCustomer().getName())
                  .phoneNumber(request.getNewCustomer().getPhoneNumber())
                  .rank(DEFAULT_RANK)
                  .build());
      order.linkCustomer(customer.getId());
      return;
    }
    order.recordContactIfUnlinked(
        application.getContactName(), application.getContactPhoneNumber());
  }

  /**
   * 会員申請の受注が着く当店の顧客を、確定の時点で決め直す。当店に申請者会員の有効な関連があればその顧客を使い、無ければ台帳行と関連（根拠 {@link
   * LinkReason#MEMBER_REQUEST}）を起こす。判断の材料は「今の関連」だけ — 関連が解除された行に着け続けると完了しても
   * 会員へ達さず、付け替えられた行に着け続けると別会員へ達する。
   *
   * <p>自動で整えるのは、ログイン態が本人証明・当該店舗への申請が明示の確認であり、帰属の成立要件をこの経路が構造的に満たすため（ADR 0008）。
   * 来店時の会員コード再提示は要求しない。整備がここで漏れると、完了時の会員解決（顧客 → 有効な関連 → 会員）が空振りしてポイントが記帳されない。
   *
   * <p>起こす台帳行の氏名は本人が申請時に名乗った名前で、電話は空。店舗はプラットフォーム側プロフィール（表示名・メール）へ到達しないため、
   * 店舗が知る名前は本人がその店舗へ名乗ると決めた名前だけになる。関連の会員コードは申請時のスナップショットを写す — 会員コードは発行後に変わらない。
   *
   * <p>関連は flush まで進めて書く。同一会員・同一店舗の申請 2 件の並行確定は双方が「関連なし」を観測しうるため、収束は部分一意索引 {@link
   * DbConstraint#UQ_T_CUSTOMER_MEMBER_LINKS_ACTIVE_MEMBER} の違反として現れなければならない（掴む位置は controller —
   * {@code OrderApplicationController#confirm}）。
   */
  private String ensureCustomerForRequester(OrderApplication application, String actorEmail) {
    Optional<CustomerMemberLink> established =
        customerMemberLinkRepository.findByStoreIdAndMemberIdAndStatus(
            application.getStoreId(), application.getRequesterMemberId(), LinkStatus.ACTIVE);
    if (established.isPresent()) {
      // 関連の照会は行を押さえないため、着ける前に顧客参照の解決を通す。
      return customerReferenceResolver.resolveForWrite(established.get().getCustomerId());
    }
    Long actorId = resolveActorId(actorEmail);
    // store_id は StoreScopeStampListener が @PrePersist で採番する。ランクは他の台帳行の作成経路
    // （通常作成・電話番号からの作成）と同じ既定を明示する — 列を写像している以上、省略は DB 既定ではなく null になる。
    Customer customer =
        customerRepository.save(
            Customer.builder()
                .name(application.getRequesterDeclaredName())
                .rank(DEFAULT_RANK)
                .build());
    customerMemberLinkRepository.saveAndFlush(
        CustomerMemberLink.builder()
            .customerId(customer.getId())
            .memberId(application.getRequesterMemberId())
            .memberCode(application.getRequesterMemberCode())
            .reason(LinkReason.MEMBER_REQUEST)
            .linkedBy(actorId)
            .linkedAt(OffsetDateTime.now())
            .build());
    return customer.getId();
  }

  /**
   * 受注を完了する（会計の確定）。会計金額を確定し、会員に紐づく受注ならポイントの利用と自動付与を台帳へ記帳する。
   *
   * <p>ポイントが台帳へ入る経路をこの操作（と手動調整）に限るため、汎用更新は完了への遷移を受け付けない。
   *
   * <p>利用は付与より先に記帳する。順序が逆だと、その受注の付与で同じ受注の利用を賄えてしまう。
   *
   * <p>非会員の受注ではポイントの利用も付与も起こらない。会員に紐づかない顧客には台帳そのものが存在しない。
   *
   * <p>会員に達した完了は、記帳と同一トランザクションで受注帰属記録（根拠 COMPLETION）を残す。会員の来店履歴はこの記録だけから読むため、
   * ここで記録が生まれない受注はその会員から永久に見えない。
   *
   * <p>会員に達しなかった完了（顧客の有無は問わない）は、代わりに伝票トークンを発行して生値を応答で一度だけ返す。 事後帰属の証明はこのトークンの所持だけであり（ADR
   * 0008）、この応答を逃した客に v1 の救済経路は無い。
   */
  @StoreScoped
  @Transactional
  public OrderCompletionResponse complete(
      String id, OrderCompletionRequest request, String actorEmail) {
    Order order =
        orderRepository.findById(id).orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));

    // 検証は台帳を触るより先に済ませる。撥ねる要求が仕訳を積んだ後だと、拒否の健全さがトランザクションの
    // 巻き戻しだけに掛かる（同一トランザクション内の後続の読みには積んだ仕訳が見えてしまう）。
    if (order.getStatus() != OrderStatus.CONFIRMED) {
      throw new IllegalOrderStateTransitionException(order.getStatus(), OrderStatus.COMPLETED);
    }

    // 会計の場で確定した内訳を先に当てる。合計は行の総和として集約が導出するので、以降の判定はすべて
    // その総和を基準にする。撥ねる要求はトランザクションごと巻き戻る（台帳へはまだ何も積んでいない）。
    order.apply(
        OrderPatch.ofAccounting(
            request.getCourseName(), orderMapper.toFeeLineDrafts(request.getFeeLines())));
    // 付与の基準と利用の上限は、ポイント利用の行が入る前の総和で決める。利用の行を入れた後の合計は
    // 客が現金で払う額であり、それを基準にすると同じ会計がポイントを使うほど付与も減る。
    int chargeAmount = order.getTotalFee();

    int usePoints = request.getUsePoints() == null ? 0 : request.getUsePoints();
    if (order.getCustomerId() != null) {
      // 積み先を決める前に顧客行を押さえ、紐づけの解除・変更と直列化する。引けない顧客はそのまま
      // 非会員として進む。契約は CustomerRepository#findByIdForUpdate に記す。
      customerRepository.findByIdForUpdate(order.getCustomerId());
    }
    CustomerMemberLink link = activeLink(order).orElse(null);
    Long memberId = link == null ? null : link.getMemberId();
    if (memberId == null && usePoints > 0) {
      throw new ServiceException("非会員の受注ではポイントを利用できません");
    }
    // 会計金額を超える利用は、請求より大きい割引に相当する仕訳を台帳へ残す。意図してポイントを減らすなら
    // 理由の付く手動調整の経路があり、取り消せない完了に混ぜない。同額までは全額のポイント払いとして通す。
    if (usePoints > chargeAmount) {
      throw new ServiceException("利用ポイントは会計金額を超えられません");
    }

    int granted = 0;
    String receiptToken = null;
    if (memberId != null) {
      Long actorId = resolveActorId(actorEmail);
      // 単位の制約と残高の充足は台帳側が判定する（利用の入口が増えても規則が分かれないため）。
      if (usePoints > 0) {
        pointLedgerService.useForOrder(memberId, id, order.getStoreId(), usePoints, actorId);
      }
      granted =
          pointLedgerService.grantForOrder(memberId, id, order.getStoreId(), chargeAmount, actorId);
      // 帰属は付与の有無と独立している。0 円完了は台帳へ行を書かないが、来店した事実は記録として残す。
      // 会員コードは解決に使った関連のスナップショットをそのまま写す — 会員コードは発行後に変わらないため、
      // 関連時点の値がそのまま帰属時点の値であり、会員行が消えた後も誰の来店だったかを読めるようにする。
      orderAttributionRepository.save(
          OrderAttribution.onCompletion(id, memberId, link.getMemberCode(), OffsetDateTime.now()));
    } else {
      receiptToken = issueReceiptToken(id, chargeAmount);
    }

    // ポイント利用は減算の明細行として内訳へ入る（台帳の減算仕訳と対になる記録）。合計はそのぶん下がる。
    order.completeWith(usePoints, granted);
    orderRepository.save(order);
    // 生値がこの応答の外へ出る経路は無い（保存されるのはダイジェストだけ）。会員へ帰属した完了では null。
    return new OrderCompletionResponse(receiptToken);
  }

  /**
   * 会員へ帰属しなかった完了に伝票トークンを発行し、生値を返す。顧客の有無は問わない — 顧客に着いていても関連の無い受注は 会員から見えず、事後帰属の対象になる。
   *
   * <p>付与予定額はこの時点の付与規則で確定して持つ。申領時点の設定を読むと、同じ会計が申領の早い遅いで別のポイントになる。 0
   * 円完了にも予定額0で発行する（申領の効果は来店の可視化に閉じる）。
   *
   * <p>保存するのはダイジェストだけで、生値はここから返る応答にしか現れない。
   */
  private String issueReceiptToken(String orderId, int totalFee) {
    ReceiptTokenGenerator.GeneratedToken generated = receiptTokenGenerator.generate();
    orderReceiptTokenRepository.save(
        OrderReceiptToken.issueFor(
            orderId,
            generated.digest(),
            pointLedgerService.previewGrant(totalFee),
            OffsetDateTime.now()));
    return generated.raw();
  }

  /**
   * 完了処理の事前計算。入力された会計金額でいくら付与されるか、会員なら残高がいくらかを返す。
   *
   * <p>付与額も利用単位も確定と同じサービス（{@link PointLedgerService}）から引く。事前計算が独自に計算すると、
   * 画面に出した見込みと確定の結果が設定変更のたびに食い違う。
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public OrderCompletionPreviewResponse completionPreview(String id, int totalFee) {
    // 会計金額は要求パラメータのため、契約の下限を持てない。負の金額は付与の計算を素通りして
    // 負の付与になるので、台帳へ問い合わせる前に撥ねる。
    if (totalFee < 0) {
      throw new ServiceException("会計金額は 0 以上で指定してください");
    }
    Order order =
        orderRepository.findById(id).orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));

    Long memberId = activeLink(order).map(CustomerMemberLink::getMemberId).orElse(null);
    return OrderCompletionPreviewResponse.builder()
        .memberLinked(memberId != null)
        .pointBalance(memberId == null ? null : pointLedgerService.balance(memberId))
        .usageUnit(pointLedgerService.usageUnit())
        // 非会員の受注は確定でも付与しない。見込みだけ付与を返すと、同じ画面が出した予定と確定の結果が食い違う。
        .grantPoints(memberId == null ? 0 : pointLedgerService.previewGrant(totalFee))
        .build();
  }

  /**
   * 受注の顧客に有効な会員紐づけ。顧客が未設定か紐づけが無ければ空を返す。会員解決はこの一本道（顧客 → 有効な関連 → 会員）だけを通り、 申請者の記録（requester）へは
   * fallback しない — 申請の出所は帰属ではない。
   *
   * <p>会員行が消えて紐づけの会員 ID が欠落した行は返るが、呼出側は会員 ID の欠落を紐づけの不在と同じに扱う（残高の所在が会員 ID でしか辿れないため）。
   *
   * <p>この問い合わせ自体はロックを取らない。完了は取り消せない一方で紐づけの解除・変更はいつでも起こりうるため、 完了は呼ぶ前に顧客行を押さえる。事前計算は台帳へ積まないので押さえない。
   */
  private Optional<CustomerMemberLink> activeLink(Order order) {
    if (order.getCustomerId() == null) {
      return Optional.empty();
    }
    return customerMemberLinkRepository.findByCustomerIdAndStatus(
        order.getCustomerId(), LinkStatus.ACTIVE);
  }

  /**
   * JWT は user-id claim を持たないため、実行者は認証主体の email から解決する。
   *
   * <p>解決できない認証主体は黙って null にせず失敗させる — 追記型の台帳では実行者 null が「機構が起こした仕訳」の形であり、
   * 失効した認証セッションによる人手の操作がそれと区別できなくなる。
   */
  private Long resolveActorId(String actorEmail) {
    return platformUserRepository
        .findByEmail(actorEmail)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }

  /**
   * 確定済みの受注を理由付きで取消す。定義域は CONFIRMED → CANCELLED のみで、理由・実行者・時刻を記録に残す（ADR 0013）。
   *
   * <p>汎用更新から状態を動かす裏口を閉じた代わりに立てた専用の口。未確定申請の謝絶（{@link #decline}）とは別物で、
   * あちらが理由を持たないのは店舗がまだ受諾していない段階だから。
   *
   * <p>二度目の取消は集約が撥ねる（逐次なら 400）。同時に届いた 2 つは双方が CONFIRMED を読んで守衛を通り、 {@code @Version} の楽観ロックで敗者が 409
   * に落ちる — 記録される理由と実行者は先に commit した側のもので、 敗者を黙って成功させる方が「自分の理由が残った」と誤って伝える。
   */
  @StoreScoped
  @Transactional
  public void cancel(String id, OrderCancellationRequest request, String actorEmail) {
    Order order =
        orderRepository.findById(id).orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));
    order.cancelWith(request.getReason(), resolveActorId(actorEmail), OffsetDateTime.now());
    orderRepository.save(order);
  }

  /** 予約申請を謝絶する。理由は必須で、実行者と時刻を記録に残す（取消 ADR 0013 の先例）。確定後の取り消しは理由必須の取消操作（{@link #cancel}）に委ねる。 */
  @StoreScoped
  @Transactional
  public void declineApplication(
      String id, OrderApplicationDeclineRequest request, String actorEmail) {
    OrderApplication application =
        orderApplicationRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("予約申請が見つかりません: " + id));
    application.decline(
        request.getReason(),
        resolveActorId(actorEmail),
        OffsetDateTime.now(),
        businessDateService.currentBusinessDate());
    orderApplicationRepository.save(application);
  }

  /**
   * 指名候補の一覧（当店に在籍中のキャストを名前で絞り込んだもの）。書き込み時の指名検証と同一の述語（{@link NominatableCastLookup}）を共有する。
   *
   * <p>キャスト管理の一覧ではなくこの読み口を持つのは、指名が受注の操作だからで、候補の範囲も要る権限も受注側が決める。 管理一覧は在籍停止のキャストも返し、キャスト管理権限を要求する。
   *
   * @param search 名前の部分一致（任意）。未指定なら絞り込まない
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public List<OrderCastCandidateResponse> listCastCandidates(String search) {
    return nominatableCast.searchCandidates(storeContext.getStoreId(), search).stream()
        .map(
            cast ->
                OrderCastCandidateResponse.builder().id(cast.getId()).name(cast.getName()).build())
        .toList();
  }

  private Optional<Long> eligibleReceptionistId(String actorEmail) {
    Long storeId = storeContext.getStoreId();
    Set<Long> orderManageRoleIds =
        roleRepository.findIdsByPermissionCode(PermissionCode.ORDER_MANAGE.name());
    return platformUserRepository
        .findByEmail(actorEmail)
        .filter(user -> isEligibleReceptionist(user, storeId, orderManageRoleIds))
        .map(PlatformUser::getId);
  }

  /**
   * 受注 1 件の詳細。表示名の join を持つ読み側 projection と、行の集合を持つ集約の 2 本から組む — 1 行 1 受注の projection では明細を運べない。
   */
  private OrderResponse toResponse(Order order) {
    OrderResponse response =
        orderRepository
            .findViewById(order.getId())
            .map(orderMapper::toResponse)
            .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + order.getId()));
    response.setFeeLines(orderMapper.toFeeLineResponses(order.getFeeLines()));
    return response;
  }

  /** 行を書き戻す操作の応答。作業キューが持つ行と同じ形で返し、呼出側がその場で 1 行だけ差し替えられるようにする。 */
  private OrderWorkQueueResponse toWorkQueueResponse(String id) {
    return orderRepository
        .findViewById(id)
        .map(orderMapper::toWorkQueueResponse)
        .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));
  }

  private void validateReceptionist(Long receptionistId) {
    Long storeId = storeContext.getStoreId();
    Set<Long> orderManageRoleIds =
        roleRepository.findIdsByPermissionCode(PermissionCode.ORDER_MANAGE.name());
    platformUserRepository
        .findById(receptionistId)
        .filter(user -> isEligibleReceptionist(user, storeId, orderManageRoleIds))
        .orElseThrow(() -> new NotFoundException("受付担当者が見つかりません: " + receptionistId));
  }

  /**
   * 受付選択肢の一覧（現店舗を授権する ORDER_MANAGE 保持 STAFF）。書き込み時の {@link #validateReceptionist} と同一の適格条件を共有する。
   *
   * <p>店舗授権の絞り込みは {@link PlatformUserRepository#findAuthorizedByUserTypeOrderByDisplayNameAsc} が DB
   * 層で行う（無関係な他店舗ユーザーの ElementCollection を読み込まないため）。ロールの判定のみ {@link #isEligibleReceptionist}
   * で引き続き行う。
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public List<OrderReceptionistResponse> listReceptionists() {
    Long storeId = storeContext.getStoreId();
    Set<Long> orderManageRoleIds =
        roleRepository.findIdsByPermissionCode(PermissionCode.ORDER_MANAGE.name());
    return platformUserRepository
        .findAuthorizedByUserTypeOrderByDisplayNameAsc(UserType.STAFF, storeId)
        .stream()
        .filter(user -> isEligibleReceptionist(user, storeId, orderManageRoleIds))
        .map(
            user ->
                OrderReceptionistResponse.builder()
                    .id(user.getId())
                    .displayName(user.getDisplayName())
                    .build())
        .toList();
  }

  // 受付担当者は「有効(enabled)かつ受注管理権限（ORDER_MANAGE）を持つ STAFF」かつ「現店舗(店舗)を授権する
  // PlatformUser」でなければならない。t_users には store_id が無いため、単なる存在確認では
  // 他店舗/CAST/MEMBER も通ってしまう。停止済み(enabled=false)の口座はロール・授権を保持したままなので明示的に弾く。
  // 書き込み時の検証（validateReceptionist）と一覧（listReceptionists）が同一条件を共有する。
  private boolean isEligibleReceptionist(
      PlatformUser user, Long storeId, Set<Long> orderManageRoleIds) {
    return user.getUserType() == UserType.STAFF
        && user.getEnabled()
        && user.authorizes(storeId)
        && !Collections.disjoint(user.getRoleIds(), orderManageRoleIds);
  }

  /**
   * 受注を店舗台帳の顧客に着ける。顧客 ID の指定があればそれを、無ければ電話番号で当店の台帳を照合する。
   *
   * <p>電話番号の照合は一致件数で 3 分岐する。0 件なら録入内容で顧客を起こして紐づけ、1 件ならその顧客に紐づけ、 <b>2
   * 件以上なら自動照合を断念して顧客未設定のまま成立させる</b>（無帰属受注は正規の状態）。同店同号は正規に起こりうる —
   * 同伴者の連絡先共有や旧システムからの移行分がそれで、一致行の中に会員関連付きの行があり得る以上、機械が 1 行を選ぶことは 誤帰属の入口になる（ADR 0009）。並び順で 1
   * 行に決める形も同じ理由で採らない。
   *
   * <p>顧客に着かなかった受注には、録入された連絡先を受注側へ写す。写さないと、台帳にも受注にも残らないまま入力が消える。
   *
   * <p>既存の行へ着ける ID は {@link CustomerReferenceResolver} から得る。顧客参照を書く経路が共有する解決口で、
   * 書く直前に対象の行を押さえる。取得できない顧客（不在・他店舗）はそこで 404 になる。
   */
  private void handleCustomerLinking(OrderCreateRequest req, Order order) {
    if (req.getCustomerId() != null && !req.getCustomerId().isEmpty()) {
      order.linkCustomer(customerReferenceResolver.resolveForWrite(req.getCustomerId()));
      return;
    }
    if (req.getPhoneNumber() != null && !req.getPhoneNumber().isEmpty()) {
      List<String> matched =
          customerRepository.findAliveIdsByPhoneNumberAndStoreId(
              req.getPhoneNumber(), storeContext.getStoreId());
      if (matched.isEmpty()) {
        // 起こしたばかりの行は他の経路の書き換えに晒されていないため、解決を経ずにそのまま着ける。
        // store_id は StoreScopeStampListener が @PrePersist で採番する
        order.linkCustomer(customerRepository.save(orderMapper.toCustomer(req)).getId());
      } else if (matched.size() == 1) {
        // 照合は行を押さえない問い合わせなので、着ける前に解決を通す（照合そのものの 3 分岐は変わらない）。
        order.linkCustomer(customerReferenceResolver.resolveForWrite(matched.get(0)));
      }
    }
    order.recordContactIfUnlinked(req.getCustomerName(), req.getPhoneNumber());
  }
}
