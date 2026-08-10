package com.kizuna.order.application;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.order.api.dto.OrderCastCandidateResponse;
import com.kizuna.order.api.dto.OrderCompletionPreviewResponse;
import com.kizuna.order.api.dto.OrderCompletionRequest;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderReceptionistResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.api.dto.ReservationRequestUpdateRequest;
import com.kizuna.order.domain.IllegalOrderStateTransitionException;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.OrderView;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

  /** 指名が成立しないことを店舗スタッフへ返すときの文言。列挙を防ぐ 404 ではなく理由と対処の分かる 400 で返す。 */
  private static final String NOT_NOMINATABLE_MESSAGE = "指名できるキャストではありません。在籍中のキャストを選んでください";

  private final OrderRepository orderRepository;
  private final CustomerRepository customerRepository;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final NominatableCastLookup nominatableCast;
  private final ConfirmedShiftLookupService confirmedShiftLookupService;
  private final PointLedgerService pointLedgerService;
  private final PlatformUserRepository platformUserRepository;
  private final RoleRepository roleRepository;
  private final StoreContext storeContext;
  private final OrderMapper orderMapper;

  @StoreScoped
  @Transactional(readOnly = true)
  public Page<OrderResponse> list(String customerId, Pageable pageable) {
    // 一覧は集約を経由せず JPQL join projection で取得。customerId は顧客詳細の注文履歴用
    String filter = (customerId == null || customerId.isBlank()) ? null : customerId;
    return orderRepository.findAllViews(filter, pageable).map(orderMapper::toResponse);
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public OrderResponse get(String id) {
    return toResponse(id);
  }

  /**
   * 予約受付 inbox の未確定申請一覧。
   *
   * <p>絞り込みは DB 側で行う — 受注一覧の先頭ページを取って手元で選り分けると、確定済みの受注が積み上がった店舗で 未処理の申請が窓から落ちて見えなくなる。
   *
   * <p>絞り込んだうえで取得件数も抑える。未処理の申請は店舗が処理し終えるまで残り続けるため、無界で返すと 積み上がるほど 1 回の取得・応答・描画が重くなる。
   *
   * <p>続きの指定は「何件目か」ではなくカーソル（並びの鍵）で受ける。この一覧は行が処理で消えていく作業キューなので、
   * 件数で位置を指すと確定・謝絶のたびに後続が繰り上がり、続きを取った時点で境界の申請を飛ばす。
   *
   * @param cursor 続きの位置。null なら先頭から
   * @param requestedSize 1 回に返す件数の希望値（上限に丸められる）
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<OrderResponse> listPendingReservationRequests(
      String cursor, int requestedSize) {
    int size = CursorPage.clampSize(requestedSize);
    // 続きの有無は上限より 1 件多く取って判る。総件数の問い合わせを毎回撒かずに済む。
    Limit limit = Limit.of(size + 1);
    List<OrderView> fetched =
        cursor == null
            ? orderRepository.findPendingReservationRequestViews(limit)
            : fetchAfter(PageCursor.decode(cursor), limit);
    return CursorPage.of(fetched, size, OrderService::cursorOf).map(orderMapper::toResponse);
  }

  private List<OrderView> fetchAfter(PageCursor cursor, Limit limit) {
    return orderRepository.findPendingReservationRequestViewsAfter(
        cursor.timestampKey(), cursor.id(), limit);
  }

  /** 続きの位置は inbox の並び（受付時刻 + id）と同じ組で作る。組が並びとずれると、続きが手前へ戻るか行を飛ばす。 */
  private static String cursorOf(OrderView view) {
    return new PageCursor(view.getCreatedAt().toString(), view.getId()).encode();
  }

  @StoreScoped
  @Transactional
  public OrderResponse create(OrderCreateRequest request) {
    // MapStructを使用して基本的なフィールドをマッピング（store_id は StoreScopeStampListener が @PrePersist で採番）
    Order order = orderMapper.toEntity(request);

    // 複雑な関連ロジックの処理（顧客のスマートリンク）
    handleCustomerLinking(request, order);

    // 指名は候補一覧と同じ条件で書き込み側でも見る — 候補に出さないだけでは、キャスト ID を直接送る要求を防げない。
    // 店舗が起こす受注は常に新しい指名を立てるため、据え置きの余地は無く無条件に要求する。
    nominatableCast
        .find(storeContext.getStoreId(), request.getCastId())
        .orElseThrow(() -> new ServiceException(NOT_NOMINATABLE_MESSAGE));
    order.assignCast(request.getCastId());
    validateReceptionist(request.getReceptionistId());
    order.assignReceptionist(request.getReceptionistId());

    Order saved = orderRepository.save(order);
    return toResponse(saved.getId());
  }

  @StoreScoped
  @Transactional
  public OrderResponse update(String id, OrderUpdateRequest request) {
    Order order =
        orderRepository.findById(id).orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));

    // 完了は会計金額の確定とポイント台帳への記帳と不可分のため、専用の完了処理だけが入口になる。
    // 汎用更新から遷移できると、会計もポイントも入らないまま完了した受注が成立してしまう。
    if (OrderStatus.COMPLETED.name().equals(request.getStatus())) {
      throw new ServiceException("完了への変更は完了処理でのみ行えます");
    }

    // 未確定（CREATED）の申請に限り、状態遷移の入口は確定・謝絶の専用操作ただ一つ。汎用更新でも
    // 遷移できると、確定時の指名再検証・顧客の補完・謝絶の対象判定をすべて迂回できてしまう。
    // 確定後のキャンセルは通常の受注のライフサイクルとして汎用更新が引き続き受け持つ —
    // 受付経路と申請者スナップショットは追跡のため残り続けるので、申請かどうかだけで判定してはならない。
    if (request.getStatus() != null
        && order.isReservationRequest()
        && order.getStatus() == OrderStatus.CREATED) {
      throw new ServiceException("予約申請の状態は確定・謝絶の操作でのみ変更できます");
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
      validateReceptionist(receptionistId);
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

    // 非nullフィールドのみをドメインの部分更新コマンドとして適用
    order.apply(orderMapper.toPatch(request));

    // 関連 ID の更新（存在確認は上で済ませている）
    if (castId != null) {
      order.assignCast(castId);
    }
    if (receptionistId != null) {
      order.assignReceptionist(receptionistId);
    }

    // ステータスはドメインの遷移メソッド経由で変更（不正な遷移はドメイン例外 → 400）
    if (request.getStatus() != null) {
      order.transitionTo(parseStatus(request.getStatus()));
    }

    Order saved = orderRepository.save(order);
    return toResponse(saved.getId());
  }

  /**
   * 予約申請を確定する。受付担当が未設定（会員の Web 申請）で、確定した本人が受付候補の条件を満たす場合はその本人を受付担当として補う。
   *
   * <p>条件を満たさない実行者（店舗を授権する HQ 管理者など）では未設定のまま残し、受付担当の適格条件を確定操作で迂回させない。
   *
   * <p>顧客も未設定なら、この時点の紐づけを見て補う。申請時に紐づけが無くても、店舗が会員コードを読んで台帳に結び付けてから
   * 確定するのが初回来店の順序であり、申請時の解決だけでは受注が顧客履歴に載らないまま残る。
   */
  @StoreScoped
  @Transactional
  public OrderResponse confirm(String id, String actorEmail) {
    Order order = findReservationRequest(id);
    revalidateNomination(order);
    order.confirm();
    if (order.getReceptionistId() == null) {
      eligibleReceptionistId(actorEmail).ifPresent(order::assignReceptionist);
    }
    if (order.getCustomerId() == null && order.getRequesterMemberId() != null) {
      customerMemberLinkRepository
          .findByStoreIdAndMemberIdAndStatus(
              order.getStoreId(), order.getRequesterMemberId(), LinkStatus.ACTIVE)
          .map(CustomerMemberLink::getCustomerId)
          .ifPresent(order::linkCustomer);
    }
    orderRepository.save(order);
    return toResponse(id);
  }

  /**
   * 受注を完了する（会計の確定）。会計金額を確定し、会員に紐づく受注ならポイントの利用と自動付与を台帳へ記帳する。
   *
   * <p>ポイントが台帳へ入る経路をこの操作（と手動調整）に限るため、汎用更新は完了への遷移を受け付けない。
   *
   * <p>利用は付与より先に記帳する。順序が逆だと、その受注の付与で同じ受注の利用を賄えてしまう。
   *
   * <p>非会員の受注ではポイントの利用も付与も起こらない。会員に紐づかない顧客には台帳そのものが存在しない。
   */
  @StoreScoped
  @Transactional
  public OrderResponse complete(String id, OrderCompletionRequest request, String actorEmail) {
    Order order =
        orderRepository.findById(id).orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));

    // 検証は台帳を触るより先に済ませる。撥ねる要求が仕訳を積んだ後だと、拒否の健全さがトランザクションの
    // 巻き戻しだけに掛かる（同一トランザクション内の後続の読みには積んだ仕訳が見えてしまう）。
    if (order.getStatus() != OrderStatus.CONFIRMED) {
      throw new IllegalOrderStateTransitionException(order.getStatus(), OrderStatus.COMPLETED);
    }

    int usePoints = request.getUsePoints() == null ? 0 : request.getUsePoints();
    Long memberId = linkedMemberId(order).orElse(null);
    if (memberId == null && usePoints > 0) {
      throw new ServiceException("非会員の受注ではポイントを利用できません");
    }

    int granted = 0;
    if (memberId != null) {
      Long actorId =
          platformUserRepository.findByEmail(actorEmail).map(PlatformUser::getId).orElse(null);
      // 単位の制約と残高の充足は台帳側が判定する（利用の入口が増えても規則が分かれないため）。
      if (usePoints > 0) {
        pointLedgerService.useForOrder(memberId, id, order.getStoreId(), usePoints, actorId);
      }
      granted =
          pointLedgerService.grantForOrder(
              memberId, id, order.getStoreId(), request.getTotalFee(), actorId);
    }

    order.completeWith(request.getTotalFee(), usePoints, granted);
    orderRepository.save(order);
    return toResponse(id);
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

    Long memberId = linkedMemberId(order).orElse(null);
    return OrderCompletionPreviewResponse.builder()
        .memberLinked(memberId != null)
        .pointBalance(memberId == null ? null : pointLedgerService.balance(memberId))
        .usageUnit(pointLedgerService.usageUnit())
        .grantPoints(pointLedgerService.previewGrant(totalFee))
        .build();
  }

  /**
   * 受注の顧客に紐づく会員。顧客が未設定、紐づけが無い、または会員行が消えて紐づけの会員 ID が欠落した場合は空を返す。
   *
   * <p>会員 ID の欠落を紐づけの不在と同じに扱うのは、残高の所在が会員 ID でしか辿れないため。
   */
  private Optional<Long> linkedMemberId(Order order) {
    if (order.getCustomerId() == null) {
      return Optional.empty();
    }
    return customerMemberLinkRepository
        .findByCustomerIdAndStatus(order.getCustomerId(), LinkStatus.ACTIVE)
        .map(CustomerMemberLink::getMemberId);
  }

  /**
   * 未確定の予約申請を店舗が編集する。汎用更新（{@link #update}）と別の収口なのは、指名と受付担当を可空として扱うため —
   * 会員は指名なしで申請できるので、必須の契約しか無いと人数や備考を直すだけで指名付きの受注に変えざるを得ず、 無効になった指名を確定前に外すこともできない。
   *
   * <p>受け取った内容がそのまま新しい申請内容になる（省略＝未設定にする）。確定・謝絶と同じく対象は未確定の申請だけで、 確定後は通常の受注として汎用更新が引き続き受け持つ。
   */
  @StoreScoped
  @Transactional
  public OrderResponse updateReservationRequest(
      String id, ReservationRequestUpdateRequest request) {
    Order order = findReservationRequest(id);
    if (order.getStatus() != OrderStatus.CREATED) {
      throw new ServiceException("確定・謝絶済みの予約は編集できません");
    }

    // 検証は書き換えより先にすべて済ませる。撥ねる要求が集約を触った後だと、拒否の健全さが
    // トランザクションの巻き戻しだけに掛かる（同一トランザクション内の後続の読みには変わった値が見えてしまう）。
    if (request.getReceptionistId() != null) {
      validateReceptionist(request.getReceptionistId());
    }
    if (request.getCastId() != null) {
      // 対象は店舗スタッフなので、確定時の再検証と同じく列挙を防ぐ 404 ではなく理由と対処の分かる 400 で返す。
      // 汎用更新と違い据え置きも縛るのは、未確定の申請は確定前に直っている必要がある作業だからで、
      // 素通しは 400 を確定時へ先送りするだけになる。
      nominatableCast
          .find(order.getStoreId(), request.getCastId())
          .orElseThrow(() -> new ServiceException("指名できるキャストではありません。在籍中のキャストを選ぶか、指名を外してください"));
    }

    order.revise(
        request.getReceptionistId(), request.getCastId(), request.getPax(), request.getRemarks());

    Order saved = orderRepository.save(order);
    return toResponse(saved.getId());
  }

  /** 予約申請を謝絶する。確定前の申請のみが対象で、確定後の取り消しは通常のキャンセル経路に委ねる。 */
  @StoreScoped
  @Transactional
  public OrderResponse decline(String id) {
    Order order = findReservationRequest(id);
    order.cancelRequest();
    orderRepository.save(order);
    return toResponse(id);
  }

  /**
   * 確定・謝絶の対象となる予約申請を引く。店舗が起こした受注は、ID を知っていても申請専用の操作では変更させない — 受注のステータス変更は通常の更新経路が受け持つ。判定は予約受付 inbox
   * の抽出条件と同じ（{@link Order#isReservationRequest()}）。
   */
  private Order findReservationRequest(String id) {
    return orderRepository
        .findById(id)
        .filter(Order::isReservationRequest)
        .orElseThrow(() -> new NotFoundException("予約申請が見つかりません: " + id));
  }

  /**
   * 確定時に指名の前提を取り直す。申請から確定までの間にキャストの在籍停止や確定シフトの取り消しが起こりうるため、 申請時（MemberOrderService
   * の検証）と同じ条件を満たさなくなった指名付き申請は確定させない — そのまま確定すると、来店時に指名キャストがいない受注が成立してしまう。
   *
   * <p>申請時の検証と違い対象は店舗スタッフなので、列挙を防ぐ 404 ではなく理由の分かる 400 で返す。
   */
  private void revalidateNomination(Order order) {
    if (order.getCastId() == null) {
      return;
    }
    nominatableCast
        .find(order.getStoreId(), order.getCastId())
        .orElseThrow(() -> new ServiceException("指名キャストが在籍中でないため確定できません。内容を修正するか謝絶してください"));
    if (!confirmedShiftLookupService.hasConfirmedShift(
        order.getStoreId(), order.getCastId(), order.getBusinessDate())) {
      throw new ServiceException("指名キャストにこの日の確定シフトが無いため確定できません。内容を修正するか謝絶してください");
    }
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

  private OrderResponse toResponse(String id) {
    return orderRepository
        .findViewById(id)
        .map(orderMapper::toResponse)
        .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));
  }

  private OrderStatus parseStatus(String raw) {
    try {
      return OrderStatus.valueOf(raw);
    } catch (IllegalArgumentException e) {
      throw new ServiceException("不正な注文ステータスです: " + raw);
    }
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

  @StoreScoped
  @Transactional
  public void delete(String id) {
    Order order =
        orderRepository.findById(id).orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));
    // 未確定の申請は削除ではなく謝絶で扱う — 削除すると CANCELLED の記録が残らず、会員側の
    // 予約履歴からも消えてしまう。汎用更新の状態ガードと同じく、確定・謝絶を経た後の行は
    // 通常の受注として削除の管理操作を受け付ける。
    if (order.isReservationRequest() && order.getStatus() == OrderStatus.CREATED) {
      throw new ServiceException("未確定の予約申請は削除できません。謝絶で扱ってください");
    }
    // 完了済みの受注はポイント台帳の仕訳が order_id で参照している。削除すると FK の SET NULL で
    // 付与・利用の根拠だけが静かに失われ、残高は残ったまま出所を辿れなくなる。
    if (order.getStatus() == OrderStatus.COMPLETED) {
      throw new ServiceException("完了済みの受注は削除できません");
    }
    orderRepository.deleteById(id);
  }

  private void handleCustomerLinking(OrderCreateRequest req, Order order) {
    if (req.getCustomerId() != null && !req.getCustomerId().isEmpty()) {
      if (!customerRepository.existsById(req.getCustomerId())) {
        throw new NotFoundException("顧客が見つかりません: " + req.getCustomerId());
      }
      order.linkCustomer(req.getCustomerId());
    } else if (req.getPhoneNumber() != null && !req.getPhoneNumber().isEmpty()) {
      // 顧客の検索または作成
      Customer customer =
          customerRepository
              .findByPhoneNumberAndStoreId(req.getPhoneNumber(), storeContext.getStoreId())
              .orElseGet(
                  () -> {
                    // store_id は StoreScopeStampListener が @PrePersist で採番する
                    Customer newCustomer = orderMapper.toCustomer(req);
                    return customerRepository.save(newCustomer);
                  });
      order.linkCustomer(customer.getId());
    }
  }
}
