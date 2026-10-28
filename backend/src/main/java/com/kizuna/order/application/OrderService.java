package com.kizuna.order.application;

import com.kizuna.customer.application.CustomerProvisioningService;
import com.kizuna.customer.application.MemberRequestCustomerInput;
import com.kizuna.customer.application.NewCustomerInput;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerContactSearch;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.order.domain.ConfirmedOrderDetails;
import com.kizuna.order.domain.ContactSnapshot;
import com.kizuna.order.domain.NewStoreOrder;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.order.domain.OrderApplicationView;
import com.kizuna.order.domain.OrderFeeLineSnapshot;
import com.kizuna.order.domain.OrderPatch;
import com.kizuna.order.domain.OrderQueryCriteria;
import com.kizuna.order.domain.OrderQuote;
import com.kizuna.order.domain.OrderReceiptToken;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderView;
import com.kizuna.order.infrastructure.OrderSearchQuery;
import com.kizuna.order.infrastructure.OrderSearchQuery.OrderedRow;
import com.kizuna.order.infrastructure.ReceiptTokenGenerator;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.shift.application.ConfirmedShiftLookupService;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.application.ReceptionistEligibilityService;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

  /** 指名が成立しないことを店舗スタッフへ返すときの文言。列挙を防ぐ 404 ではなく理由と対処の分かる 400 で返す。 */
  private static final String NOT_NOMINATABLE_MESSAGE = "指名できるキャストではありません。在籍中のキャストを選んでください";

  private final OrderRepository orderRepository;
  private final BusinessAudit businessAudit;
  private final OrderApplicationRepository orderApplicationRepository;
  private final OrderSearchQuery orderSearchQuery;
  private final OrderReceiptTokenRepository orderReceiptTokenRepository;
  private final ReceiptTokenGenerator receiptTokenGenerator;
  private final CustomerRepository customerRepository;
  private final CustomerContactRepository customerContactRepository;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final CustomerProvisioningService customerProvisioningService;
  private final NominatableCastLookup nominatableCast;

  private RuntimeException eligibilityFailure(boolean confirmedInput, String message) {
    return !confirmedInput
        ? new ServiceException(message)
        : new OrderConfirmationConflict("confirmation_token");
  }

  private final ConfirmedShiftLookupService confirmedShiftLookupService;
  private final PointLedgerService pointLedgerService;
  private final AttributionMaterializer materializer;
  private final ActorIdentityService actorIdentityService;
  private final BusinessContactPermissions businessContactPermissions;
  private final GuestApplicationConsent guestConsent;
  private final ReceptionistEligibilityService receptionistEligibilityService;
  private final BusinessDateService businessDateService;
  private final OrderCalculation calculation;
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
   * 店舗が起こす受注を作成する。この経路の受注は<b>確定で出生する</b>（出生状態は下の Order 生成で CONFIRMED に固定される）— 電話口で受けると決めた時点で
   * 可否は判断済みであり、画面上でもう一度確定し直す段は無い。
   */
  @Transactional
  public OrderDetail create(
      Long storeId, OrderCreateCommand command, String actorEmail, boolean canManageCustomers) {
    specialServices.lock(storeId);
    validateReplacement(storeId, command.replacementForOrderId());
    // Web 申請の経路（MEMBER_WEB / GUEST_WEB）は申請の確定だけが書く値。台帳を触るより先に撥ねる —
    // 広告費と効果集計の根拠になる記録が代理入力で偽装されると、後から申請と手入力を切り分ける手立てが無い。
    if (command.receptionRoute() != null && !command.receptionRoute().isStoreSelectable()) {
      throw new ServiceException("受付経路に Web 申請は指定できません。予約申請の確定だけが MEMBER_WEB / GUEST_WEB を名乗ります");
    }

    // 明細の店舗は採用時に受注から写すため、採用より先に置く（出生時に店舗が付く）
    Order order =
        Order.placedByStore(
            storeId,
            new NewStoreOrder(
                command.replacementForOrderId(),
                command.businessDate(),
                command.arrivalScheduledStartTime(),
                command.arrivalScheduledEndTime(),
                command.address(),
                command.buildingName(),
                command.pax(),
                command.receptionRoute(),
                command.carrier(),
                command.mediaName(),
                command.remarks(),
                command.castDriverMessage()));

    // 指名は候補一覧と同じ条件で書き込み側でも見る — 候補に出さないだけでは、キャスト ID を直接送る要求を防げない。
    // 店舗が起こす受注は常に新しい指名を立てるため、据え置きの余地は無く無条件に要求する。
    nominatableCast
        .findForUpdate(storeId, command.castId())
        .orElseThrow(
            () ->
                eligibilityFailure(
                    calculation.wasPreviewed(
                        storeId, "CREATE", "", actorEmail, command, command.confirmationToken()),
                    NOT_NOMINATABLE_MESSAGE));
    var course = calculation.current(storeId, command.courseId(), true);
    var calculated =
        calculation.calculate(
            storeId,
            null,
            course,
            command.feeLines(),
            specialServices.select(
                storeId,
                null,
                command.castId(),
                command.specialServiceIds(),
                calculation.wasPreviewed(
                    storeId, "CREATE", "", actorEmail, command, command.confirmationToken())),
            true);
    order.adoptServices(course, calculated.specialServices(), calculated.editableFeeLines());

    order.linkCustomer(
        selectCustomer(storeId, command.customerSelection(), true, canManageCustomers));
    order.replaceContact(snapshot(command.contactSnapshot()));
    calculation.verify(
        command.confirmationToken(),
        storeId,
        "CREATE",
        "",
        actorEmail,
        command,
        calculated,
        completionPoints(storeId, order.getCustomerId(), calculated, 0));

    order.assignCast(command.castId());
    order.assignReceptionist(resolveReceptionist(storeId, command.receptionistId(), actorEmail));

    Order saved = orderRepository.save(order);
    orderRepository.flush();
    businessContactPermissions.record(
        saved,
        ContactSnapshot.empty(),
        command.businessContactPermissions(),
        actorEmail,
        OffsetDateTime.now());
    auditOrder(saved, "ORDER_CREATED", actorEmail, null, null, null);
    return toDetail(storeId, saved);
  }

  private void validateReplacement(Long storeId, String id) {
    if (id == null) return;
    var original =
        orderRepository
            .findByIdAndStoreIdForUpdate(id, storeId)
            .orElseThrow(() -> new NotFoundException("再提供の元受注が見つかりません"));
    if (!original.isCompletionInvalidated()) throw new ServiceException("無効化した受注だけを再提供の元受注に指定できます");
  }

  /** 作成時は適格な受付担当を必須とし、省略時も実行者本人の適格性を検証する。 会員申請の確定と異なり既に成立した申請を引き継がないため、未設定のまま受注を起こさない。 */
  private Long resolveReceptionist(Long storeId, Long requested, String actorEmail) {
    if (requested != null) {
      validateReceptionist(storeId, requested);
      return requested;
    }
    return eligibleReceptionistId(storeId, actorEmail)
        .orElseThrow(() -> new ServiceException("受付担当を指定してください"));
  }

  /** 未完了の受注だけを部分更新する。状態遷移と完了後の訂正は、それぞれの専用操作で検証する。 */
  @Transactional
  public OrderDetail update(
      Long storeId,
      String id,
      OrderUpdateCommand command,
      String actorEmail,
      boolean canManageCustomers) {
    specialServices.lock(storeId);
    Order order =
        orderRepository
            .findByIdAndStoreIdForUpdate(id, storeId)
            .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));

    order.requireVersion(command.expectedVersion());

    // 完了後の内容訂正は権限付き・記録付きの別経路が担う。
    order.requireEditable();

    var before = OrderAuditSnapshot.of(order);

    // 空文字は「送っていない」と同じに扱う。編集画面の未選択がそのまま乗ってくる形なので、存在しない
    // キャストとして扱って 404 を返すより、指名なしの要求として同じ判定に載せる方が呼び手に意味が通る。
    String castId =
        (command.castId() == null || command.castId().isBlank()) ? null : command.castId();
    Long receptionistId = command.receptionistId();

    validateUpdateAssignments(
        storeId,
        order,
        castId,
        receptionistId,
        calculation.wasPreviewed(
            storeId, "UPDATE", id, actorEmail, command, command.confirmationToken()));

    validateEditableCustomer(order, command.customerSelection());
    if (command.customerSelection() != null)
      order.linkCustomer(
          selectCustomer(storeId, command.customerSelection(), true, canManageCustomers));
    var previousContact = order.getContactSnapshot();
    if (command.contactSnapshot() != null)
      order.replaceContact(snapshot(command.contactSnapshot()));

    var course =
        command.courseId() == null
            ? order.getCourse()
            : calculation.current(storeId, command.courseId(), true);
    var calculated =
        calculation.calculate(
            storeId,
            order,
            course,
            command.feeLines(),
            specialServices.select(
                storeId,
                order,
                castId,
                command.specialServiceIds(),
                calculation.wasPreviewed(
                    storeId, "UPDATE", id, actorEmail, command, command.confirmationToken())),
            true);
    lockCustomerWithoutWaiting(storeId, order);
    calculation.verify(
        command.confirmationToken(),
        storeId,
        "UPDATE",
        id,
        actorEmail,
        command,
        calculated,
        completionPoints(storeId, order.getCustomerId(), calculated, 0));
    var previousSpecials = order.getSpecialServices();
    int previousTotal = order.getTotalFee();
    String previousCast = order.getCastId();
    order.adoptServices(course, calculated.specialServices(), calculated.editableFeeLines());
    order.apply(
        new OrderPatch(
            command.businessDate(),
            command.arrivalScheduledStartTime(),
            command.arrivalScheduledEndTime(),
            command.pax(),
            null,
            command.locationAddress(),
            command.locationBuilding(),
            command.carrier(),
            command.mediaName(),
            command.remarks(),
            command.castDriverMessage()));

    if (castId != null) {
      order.assignCast(castId);
    }
    if (receptionistId != null) {
      order.assignReceptionist(receptionistId);
    }

    var now = OffsetDateTime.now();
    specialServices.resolve(
        storeId, order, previousSpecials, previousTotal, previousCast, actorEmail, now);
    businessContactPermissions.record(
        order, previousContact, command.businessContactPermissions(), actorEmail, now);

    Order saved = orderRepository.save(order);
    auditOrder(saved, "ORDER_UPDATED", actorEmail, before, null, null);
    return toDetail(storeId, saved);
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
    return CursorPage.of(fetched, size, OrderService::applicationCursorOf)
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
   * <p>顧客は確定の時点で決め直す。完了時の会員解決は顧客の現在の関連だけを見る （ADR 0008 の一本道）ため、古い参照を持ち回ると申請者の来店とポイントが別会員へ積まれる。
   */
  @Transactional
  public OrderDetail confirmApplication(
      Long storeId,
      String id,
      OrderApplicationConfirmationCommand command,
      String actorEmail,
      boolean canManageCustomers) {
    specialServices.lock(storeId);
    OrderApplication application =
        orderApplicationRepository
            .findByIdAndStoreIdForUpdate(id, storeId)
            .orElseThrow(() -> new NotFoundException("予約申請が見つかりません: " + id));

    // 検証は書き換えより先にすべて済ませる。撥ねる要求が集約や台帳を触った後だと、拒否の健全さが
    // トランザクションの巻き戻しだけに掛かる。
    var before = OrderAuditSnapshot.application(application);
    LocalDate today = businessDateService.currentBusinessDate();
    application.ensureDecidable(today);
    if (command.castId() != null) {
      nominatableCast
          .findForUpdate(storeId, command.castId())
          .orElseThrow(
              () ->
                  eligibilityFailure(
                      calculation.wasPreviewed(
                          storeId, "CONFIRM", id, actorEmail, command, command.confirmationToken()),
                      "指名キャストが在籍中でないため確定できません。内容を修正するか謝絶してください"));
      if (!confirmedShiftLookupService.hasConfirmedShift(
          storeId, command.castId(), command.businessDate())) {
        throw eligibilityFailure(
            calculation.wasPreviewed(
                storeId, "CONFIRM", id, actorEmail, command, command.confirmationToken()),
            "指名キャストにこの日の確定シフトが無いため確定できません。内容を修正するか謝絶してください");
      }
    }
    if (command.receptionistId() != null) {
      validateReceptionist(storeId, command.receptionistId());
    }
    validateCustomerChoice(storeId, application, command, canManageCustomers);
    var contactImports = guestConsent.previewImports(application, command);
    Long actorId = actorIdentityService.requireUserId(actorEmail);

    var course = calculation.current(storeId, command.courseId(), true);
    var calculated =
        calculation.calculate(
            storeId,
            null,
            course,
            command.feeLines(),
            specialServices.select(
                storeId,
                null,
                command.castId(),
                command.specialServiceIds(),
                calculation.wasPreviewed(
                    storeId, "CONFIRM", id, actorEmail, command, command.confirmationToken())),
            true);
    Order order =
        Order.confirmedFrom(
            storeId,
            application,
            new ConfirmedOrderDetails(
                command.businessDate(),
                command.arrivalScheduledStartTime(),
                command.arrivalScheduledEndTime(),
                command.pax(),
                command.remarks()));
    order.adoptServices(course, calculated.specialServices(), calculated.editableFeeLines());
    if (command.castId() != null) {
      order.assignCast(command.castId());
    }
    if (command.receptionistId() != null) {
      order.assignReceptionist(command.receptionistId());
    } else {
      eligibleReceptionistId(storeId, actorEmail).ifPresent(order::assignReceptionist);
    }
    if (application.isGuest()) {
      order.linkCustomer(
          selectCustomer(storeId, command.customerSelection(), true, canManageCustomers));
    } else if (application.getRequesterMemberId() != null) {
      // 申請者の会員 ID が欠落した申請（会員行の削除後）は整える先が無いため、顧客未設定のまま成立させる
      // （無帰属受注は正規の状態）。
      order.linkCustomer(
          customerProvisioningService.ensureMemberRequestCustomer(
              storeId,
              new MemberRequestCustomerInput(
                  application.getRequesterMemberId(),
                  application.getRequesterMemberCode(),
                  application.getRequesterDeclaredName(),
                  actorId)));
    }
    order.replaceContact(
        command.contactSnapshot() != null
            ? snapshot(command.contactSnapshot())
            : application.isGuest()
                ? ContactSnapshot.normalize(
                    application.getContactName(),
                    application.getContactPhoneNumber(),
                    application.getContactEmail(),
                    application.getContactLineId())
                : ContactSnapshot.normalize(
                    application.getRequesterDeclaredName(), null, null, null));
    calculation.verify(
        command.confirmationToken(),
        storeId,
        "CONFIRM",
        id,
        actorEmail,
        command,
        calculated,
        completionPoints(storeId, order.getCustomerId(), calculated, 0),
        contactImports);
    Order saved = orderRepository.save(order);
    var now = OffsetDateTime.now();
    businessContactPermissions.record(
        saved,
        ContactSnapshot.empty(),
        guestConsent.forOrder(application, saved.getContactSnapshot()),
        actorEmail,
        now);
    guestConsent.importContacts(application, saved.getCustomerId(), contactImports, actorId);
    application.confirmWith(saved.getId(), actorId, now, today);
    orderApplicationRepository.save(application);
    auditOrder(saved, "ORDER_CREATED", actorEmail, null, "ORDER_APPLICATION", application.getId());
    auditApplication(application, "ORDER_APPLICATION_CONFIRMED", actorEmail, before);
    return toDetail(storeId, saved);
  }

  private void validateCustomerChoice(
      Long storeId,
      OrderApplication application,
      OrderApplicationConfirmationCommand command,
      boolean canManageCustomers) {
    if (!application.isGuest() && command.customerSelection() != null)
      throw new ServiceException("会員の申請では顧客を選べません。顧客は会員の紐づけから決まります");
    if (application.isGuest())
      selectCustomer(storeId, command.customerSelection(), false, canManageCustomers);
    if (command.contactSnapshot() != null) snapshot(command.contactSnapshot());
  }

  /** 会計と会員への帰属を同一トランザクションで確定する。汎用更新からは完了へ遷移させない。 会員に達しない受注では、事後帰属の所持証明となる伝票トークンを発行して生値を返す。 */
  @Transactional
  public OrderCompletionResult complete(
      Long storeId, String id, OrderCompletionCommand command, String actorEmail) {
    specialServices.lock(storeId);
    Order order =
        orderRepository
            .findByIdAndStoreIdForUpdate(id, storeId)
            .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));

    // 検証は台帳を触るより先に済ませる。撥ねる要求が仕訳を積んだ後だと、拒否の健全さがトランザクションの
    // 巻き戻しだけに掛かる（同一トランザクション内の後続の読みには積んだ仕訳が見えてしまう）。
    order.requireCompletable(command.expectedVersion());

    var before = OrderAuditSnapshot.of(order);
    specialServices.requireProgress(storeId, order);
    var calculated =
        calculation.calculate(storeId, order, order.getCourse(), command.feeLines(), true);
    // 付与の基準と利用の上限は、ポイント利用の行を除いた総和で決める。利用の行を入れた後の合計は
    // ポイント控除後の請求額であり、それを基準にすると同じ会計がポイントを使うほど付与も減る。
    // 受注はまだ書き換えていないので、採用後の明細は試算の写しから読む。
    int chargeAmount = calculated.grantBasisAmount();

    int usePoints = command.usedPoints();
    lockCustomerWithoutWaiting(storeId, order);
    CustomerMemberLink link = activeLink(storeId, order.getCustomerId()).orElse(null);
    Long memberId = link == null ? null : link.getMemberId();
    boolean previewed =
        calculation.wasPreviewed(
            storeId, "COMPLETE", id, actorEmail, command, command.confirmationToken());
    // 試算した時には会員だった受注の紐づけが外れた。内容の誤りではないので 409 で再試算へ差し戻す。
    if (memberId == null && usePoints > 0 && previewed)
      throw new OrderConfirmationConflict("confirmation_token");
    calculated.requireRedeemable(usePoints, memberId != null);

    var now = OffsetDateTime.now();
    var pointsPreview =
        orderPoints(
            memberId,
            link == null ? null : link.getMemberCode(),
            chargeAmount,
            usePoints,
            previewed);
    var completed = order.quoteCompletion(calculated, usePoints, pointsPreview.grantPoints(), now);
    calculation.verify(
        command.confirmationToken(),
        storeId,
        "COMPLETE",
        id,
        actorEmail,
        command,
        completed,
        pointsPreview);
    order.replaceStoreFeeLines(calculated.editableFeeLines());
    int granted = 0;
    String receiptToken = null;
    if (memberId != null) {
      Long actorId = actorIdentityService.requireUserId(actorEmail);
      granted =
          materializer
              .materialize(
                  memberId,
                  link.getMemberCode(),
                  id,
                  order.getStoreId(),
                  order.getBusinessDate(),
                  now,
                  actorId,
                  new AttributionMaterializer.Completion(chargeAmount, usePoints))
              .grantedPoints();
    } else {
      receiptToken = issueReceiptToken(id, chargeAmount);
    }

    if (granted != pointsPreview.grantPoints()
        || pointLedgerService.usageUnit() != pointsPreview.usageUnit()) {
      throw new OrderConfirmationConflict("confirmation_token");
    }
    // ポイント利用は減算の明細行として内訳へ入る（台帳の減算仕訳と対になる記録）。合計はそのぶん下がる。
    order.completeWith(usePoints, granted, now);
    orderRepository.save(order);
    auditOrder(order, "ORDER_COMPLETED", actorEmail, before, null, null);
    // 生値がこの応答の外へ出る経路は無い（保存されるのはダイジェストだけ）。会員へ帰属した完了では null。
    return new OrderCompletionResult(receiptToken);
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

  @Transactional
  public OrderPreview previewCreate(
      Long storeId, OrderCreateCommand command, String actorEmail, boolean canManageCustomers) {
    specialServices.lock(storeId);
    validateReplacement(storeId, command.replacementForOrderId());
    calculation.requirePreviewInput(command.confirmationToken());
    if (command.receptionRoute() != null && !command.receptionRoute().isStoreSelectable())
      throw new ServiceException("受付経路に Web 申請は指定できません");
    nominatableCast
        .findForUpdate(storeId, command.castId())
        .orElseThrow(() -> new ServiceException(NOT_NOMINATABLE_MESSAGE));
    resolveReceptionist(storeId, command.receptionistId(), actorEmail);
    businessContactPermissions.validate(
        snapshot(command.contactSnapshot()), command.businessContactPermissions());
    var course = calculation.current(storeId, command.courseId(), false);
    var calculated =
        calculation.calculate(
            storeId,
            null,
            course,
            command.feeLines(),
            specialServices.select(
                storeId, null, command.castId(), command.specialServiceIds(), false),
            false);
    String customerId =
        selectCustomer(storeId, command.customerSelection(), false, canManageCustomers);
    return calculation.preview(
        storeId,
        "CREATE",
        "",
        actorEmail,
        command,
        calculated,
        completionPoints(storeId, customerId, calculated, 0));
  }

  @Transactional
  public OrderPreview previewConfirmation(
      Long storeId,
      String id,
      OrderApplicationConfirmationCommand command,
      String actorEmail,
      boolean canManageCustomers) {
    specialServices.lock(storeId);
    calculation.requirePreviewInput(command.confirmationToken());
    var application =
        orderApplicationRepository
            .findByIdAndStoreIdForUpdate(id, storeId)
            .orElseThrow(() -> new NotFoundException("予約申請が見つかりません"));
    application.ensureDecidable(businessDateService.currentBusinessDate());
    validateCustomerChoice(storeId, application, command, canManageCustomers);
    if (command.castId() != null) {
      nominatableCast
          .findForUpdate(storeId, command.castId())
          .orElseThrow(() -> new ServiceException(NOT_NOMINATABLE_MESSAGE));
      if (!confirmedShiftLookupService.hasConfirmedShift(
          storeId, command.castId(), command.businessDate()))
        throw new ServiceException("確定シフトがありません");
    }
    if (command.receptionistId() != null) validateReceptionist(storeId, command.receptionistId());
    snapshot(command.contactSnapshot());
    var course = calculation.current(storeId, command.courseId(), false);
    var calculated =
        calculation.calculate(
            storeId,
            null,
            course,
            command.feeLines(),
            specialServices.select(
                storeId, null, command.castId(), command.specialServiceIds(), false),
            false);
    OrderPoints points;
    if (!application.isGuest() && application.getRequesterMemberId() != null) {
      points =
          orderPoints(
              application.getRequesterMemberId(),
              application.getRequesterMemberCode(),
              calculated.grantBasisAmount(),
              0,
              false);
    } else {
      String customerId =
          application.isGuest()
              ? selectCustomer(storeId, command.customerSelection(), false, canManageCustomers)
              : null;
      points = completionPoints(storeId, customerId, calculated, 0);
    }
    return calculation.preview(
        storeId,
        "CONFIRM",
        id,
        actorEmail,
        command,
        calculated,
        points,
        guestConsent.previewImports(application, command));
  }

  private void validateUpdateAssignments(
      Long storeId, Order order, String castId, Long receptionistId, boolean confirmedInput) {
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
        validateReceptionist(storeId, receptionistId);
      }
    } else if (order.getReceptionistId() != null) {
      throw new ServiceException("受付担当を外すことはできません。受付担当を指定してください");
    }
    if (castId != null) {
      // 縛るのは新しく立てる指名と差し替えだけで、据え置き（同じ指名の再送）は素通しする。この経路は指名済みの
      // 受注に cast_id の再送を必須にしているため、無条件に在籍中を要求すると、指名者が在籍停止になった未完了の
      // 受注が備考・人数の修正も完了への遷移もできなくなる。据え置かれた指名は成立した時点で検証済みで、
      // cast_id には FK も掛かっているので、素通しが存在しないキャストを通すことにはならない。
      if (!castId.equals(order.getCastId())) {
        nominatableCast
            .findForUpdate(storeId, castId)
            .orElseThrow(() -> eligibilityFailure(confirmedInput, NOT_NOMINATABLE_MESSAGE));
      }
    } else if (order.getCastId() != null) {
      throw new ServiceException("指名を外すことはできません。キャストを指定してください");
    }
  }

  @Transactional
  public OrderPreview previewUpdate(
      Long storeId,
      String id,
      OrderUpdateCommand command,
      String actorEmail,
      boolean canManageCustomers) {
    specialServices.lock(storeId);
    calculation.requirePreviewInput(command.confirmationToken());
    var order =
        orderRepository
            .findByIdAndStoreIdForUpdate(id, storeId)
            .orElseThrow(() -> new NotFoundException("受注が見つかりません"));
    order.requireVersion(command.expectedVersion());
    order.requireEditable();
    String castId =
        command.castId() == null || command.castId().isBlank() ? null : command.castId();
    validateUpdateAssignments(storeId, order, castId, command.receptionistId(), false);
    validateEditableCustomer(order, command.customerSelection());
    businessContactPermissions.validate(
        command.contactSnapshot() == null
            ? order.getContactSnapshot()
            : snapshot(command.contactSnapshot()),
        command.businessContactPermissions());
    var course =
        command.courseId() == null
            ? order.getCourse()
            : calculation.current(storeId, command.courseId(), false);
    var calculated =
        calculation.calculate(
            storeId,
            order,
            course,
            command.feeLines(),
            specialServices.select(storeId, order, castId, command.specialServiceIds(), false),
            false);
    String customerId =
        command.customerSelection() != null
            ? selectCustomer(storeId, command.customerSelection(), false, canManageCustomers)
            : order.getCustomerId();
    return calculation.preview(
        storeId,
        "UPDATE",
        id,
        actorEmail,
        command,
        calculated,
        completionPoints(storeId, customerId, calculated, 0));
  }

  @Transactional
  public OrderPreview completionPreview(
      Long storeId, String id, OrderCompletionCommand command, String actorEmail) {
    specialServices.lock(storeId);
    calculation.requirePreviewInput(command.confirmationToken());
    var order =
        orderRepository
            .findByIdAndStoreIdForUpdate(id, storeId)
            .orElseThrow(() -> new NotFoundException("受注が見つかりません"));
    order.requireCompletable(command.expectedVersion());
    specialServices.requireProgress(storeId, order);
    lockCustomerWithoutWaiting(storeId, order);
    var calculated =
        calculation.calculate(storeId, order, order.getCourse(), command.feeLines(), false);
    int usePoints = command.usedPoints();
    var link = activeLink(storeId, order.getCustomerId()).orElse(null);
    Long memberId = link == null ? null : link.getMemberId();
    calculated.requireRedeemable(usePoints, memberId != null);
    var points =
        orderPoints(
            memberId,
            link == null ? null : link.getMemberCode(),
            calculated.grantBasisAmount(),
            usePoints,
            false);
    var completed =
        order.quoteCompletion(calculated, usePoints, points.grantPoints(), OffsetDateTime.now());
    return calculation.preview(storeId, "COMPLETE", id, actorEmail, command, completed, points);
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

  private static ContactSnapshot snapshot(ContactSnapshotInput input) {
    return input == null ? ContactSnapshot.empty() : input.normalized();
  }

  private void validateEditableCustomer(Order order, CustomerSelection selection) {
    if (selection == null) return;
    selection.validate();
    if (order.getRequesterMemberCode() != null
        && (selection.mode() == CustomerSelection.Mode.NEW
            || !Objects.equals(order.getCustomerId(), selection.customerId())))
      throw new ServiceException("会員申請の顧客は変更できません");
  }

  private String selectCustomer(
      Long storeId, CustomerSelection selection, boolean create, boolean canManageCustomers) {
    if (selection == null) throw new ServiceException("顧客の選択方法を指定してください");
    selection.validate();
    return switch (selection.mode()) {
      case NONE -> null;
      case NEW -> {
        if (!canManageCustomers) throw new AccessDeniedException("顧客の新規登録には顧客管理権限が必要です");
        yield create
            ? customerProvisioningService.createCustomer(
                storeId, new NewCustomerInput(selection.newCustomer().name()))
            : null;
      }
      case EXISTING -> {
        // 受注を保持する編集と顧客統合のロック順序が逆になるため、顧客の取得では待たない。
        try {
          var customer =
              customerRepository
                  .findByIdAndStoreIdForUpdateNoWait(selection.customerId(), storeId)
                  .orElseThrow(() -> new NotFoundException("顧客が見つかりません"));
          if (customer.getMergedIntoId() != null) throw new NotFoundException("顧客が見つかりません");
          yield customer.getId();
        } catch (CannotAcquireLockException ex) {
          throw new ConflictException("顧客情報が変更中です。入力を保持して再試算してください");
        }
      }
    };
  }

  /** 受注行を保持したまま顧客行を待つと、顧客から受注へ進む統合と循環するため、競合時は巻き戻す。 */
  private void lockCustomerWithoutWaiting(Long storeId, Order order) {
    if (order.getCustomerId() == null) return;
    try {
      customerRepository.findByIdAndStoreIdForUpdateNoWait(order.getCustomerId(), storeId);
    } catch (CannotAcquireLockException ex) {
      throw new ConflictException(
          "顧客情報が変更中です。しばらく待ってから再試算してください", Map.of("customer_lock", "入力を保持したまま、しばらく待ってから再試算してください"));
    }
  }

  private OrderPoints completionPoints(
      Long storeId, String customerId, OrderQuote quote, int usePoints) {
    var link = activeLink(storeId, customerId).orElse(null);
    return orderPoints(
        link == null ? null : link.getMemberId(),
        link == null ? null : link.getMemberCode(),
        quote.grantBasisAmount(),
        usePoints,
        false);
  }

  /** 利用の可否は台帳が判じる。試算済みの要求で否なら、残高や単位が試算の後に変わったので 409 で再試算へ戻す。 */
  private OrderPoints orderPoints(
      Long memberId, String memberCode, int basis, int usePoints, boolean confirmed) {
    if (memberId == null) return OrderPoints.nonMember(pointLedgerService.usageUnit(), usePoints);
    var quote = pointLedgerService.quote(memberId, basis, usePoints);
    if (quote.rejection() != null) {
      if (confirmed) throw new OrderConfirmationConflict("use_points", quote.rejection());
      throw new ServiceException(quote.rejection());
    }
    return OrderPoints.member(memberCode, usePoints, quote);
  }

  /**
   * 会員は顧客の ACTIVE 関連だけから解決し、申請者の記録へは代替しない。削除済み会員の ID 欠落は未紐づけとして扱う。
   * この問い合わせ自体はロックを取らない。通常更新・完了・完了試算は呼出前に顧客行を NOWAIT で押さえ、 取得競合は customer_lock の 409
   * で返す。他の試算は顧客行のロックをこの問い合わせの前提としない。
   */
  private Optional<CustomerMemberLink> activeLink(Long storeId, String customerId) {
    if (customerId == null) {
      return Optional.empty();
    }
    return customerMemberLinkRepository.findByStoreIdAndCustomerIdAndStatus(
        storeId, customerId, LinkStatus.ACTIVE);
  }

  /**
   * 未完了（CONFIRMED / IN_SERVICE）の受注を理由付きで取消し、実行者・時刻を記録する。
   * 店舗・受注のロックで並行操作を直列化し、二度目の取消は状態違反として拒否する（400）。
   */
  @Transactional
  public void cancel(Long storeId, String id, String reason, String actorEmail) {
    specialServices.lock(storeId);
    Order order =
        orderRepository
            .findByIdAndStoreIdForUpdate(id, storeId)
            .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));
    var before = OrderAuditSnapshot.of(order);
    order.cancelWith(reason, actorIdentityService.requireUserId(actorEmail), OffsetDateTime.now());
    orderRepository.save(order);
    auditOrder(order, "ORDER_CANCELLED", actorEmail, before, null, null);
  }

  /** 予約申請を謝絶する。理由は必須で、実行者と時刻を記録に残す（取消 ADR 0013 の先例）。確定後の取り消しは理由必須の取消操作（{@link #cancel}）に委ねる。 */
  @Transactional
  public void declineApplication(Long storeId, String id, String reason, String actorEmail) {
    OrderApplication application =
        orderApplicationRepository
            .findByIdAndStoreId(id, storeId)
            .orElseThrow(() -> new NotFoundException("予約申請が見つかりません: " + id));
    var before = OrderAuditSnapshot.application(application);
    application.decline(
        reason,
        actorIdentityService.requireUserId(actorEmail),
        OffsetDateTime.now(),
        businessDateService.currentBusinessDate());
    orderApplicationRepository.save(application);
    auditApplication(application, "ORDER_APPLICATION_DECLINED", actorEmail, before);
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

  @Transactional
  public OrderDetail start(
      Long storeId, String id, Long expectedVersion, String reason, String actor) {
    specialServices.lock(storeId);
    var order =
        orderRepository
            .findByIdAndStoreIdForUpdate(id, storeId)
            .orElseThrow(() -> new NotFoundException("受注が見つかりません"));
    order.requireVersion(expectedVersion);
    var before = OrderAuditSnapshot.of(order);
    order.start(reason, actorIdentityService.requireUserId(actor), OffsetDateTime.now());
    specialServices.requireProgress(storeId, order);
    orderRepository.saveAndFlush(order);
    auditOrder(order, "ORDER_STARTED", actor, before, null, null);
    return toDetail(storeId, order);
  }

  private void auditOrder(
      Order order,
      String action,
      String actor,
      OrderAuditSnapshot before,
      String sourceType,
      String sourceId) {
    orderRepository.flush();
    var after = OrderAuditSnapshot.of(order);
    businessAudit.record(
        actor,
        order.getStoreId(),
        action,
        "ORDER",
        order.getId(),
        sourceType,
        sourceId,
        before == null ? Map.of() : before.values(),
        after.after(before));
  }

  private void auditApplication(
      OrderApplication application, String action, String actor, Map<String, String> before) {
    orderApplicationRepository.flush();
    businessAudit.record(
        actor,
        application.getStoreId(),
        action,
        "ORDER_APPLICATION",
        application.getId(),
        null,
        null,
        before,
        OrderAuditSnapshot.application(application));
  }

  private Optional<Long> eligibleReceptionistId(Long storeId, String actorEmail) {
    return receptionistEligibilityService.findEligibleIdByEmail(actorEmail, storeId);
  }

  /**
   * 受注 1 件の詳細。表示名の join を持つ読み側 projection と、行の集合を持つ集約の 2 本から組む — 1 行 1 受注の projection では明細を運べない。
   */
  private OrderDetail toDetail(Long storeId, Order order) {
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

  private void validateReceptionist(Long storeId, Long receptionistId) {
    if (!receptionistEligibilityService.isEligible(receptionistId, storeId)) {
      throw new NotFoundException("受付担当者が見つかりません: " + receptionistId);
    }
  }

  @Transactional(readOnly = true)
  public List<OrderReceptionistCandidate> listReceptionists(Long storeId) {
    return receptionistEligibilityService.listCandidates(storeId).stream()
        .map(candidate -> new OrderReceptionistCandidate(candidate.id(), candidate.displayName()))
        .toList();
  }
}
