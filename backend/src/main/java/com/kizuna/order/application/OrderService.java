package com.kizuna.order.application;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderReceptionistResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.api.dto.ReservationRequestUpdateRequest;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

  /** 指名を成立させるキャストの在籍状態。申請時の検証（MemberOrderService）と同じ条件を確定時にも用いる。 */
  private static final String ACTIVE_CAST_STATUS = "ACTIVE";

  private final OrderRepository orderRepository;
  private final CustomerRepository customerRepository;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final CastRepository castRepository;
  private final ConfirmedShiftLookupService confirmedShiftLookupService;
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
   * <p>絞り込んだうえでページングもする。未処理の申請は店舗が処理し終えるまで残り続けるため、無界で返すと 積み上がるほど 1
   * 回の取得・応答・描画が重くなる。総件数を伴うので、上限を超えた分にも呼出側が到達できる。
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public Page<OrderResponse> listPendingReservationRequests(Pageable pageable) {
    return orderRepository
        .findPendingReservationRequestViews(pageable)
        .map(orderMapper::toResponse);
  }

  @StoreScoped
  @Transactional
  public OrderResponse create(OrderCreateRequest request) {
    // MapStructを使用して基本的なフィールドをマッピング（store_id は StoreScopeStampListener が @PrePersist で採番）
    Order order = orderMapper.toEntity(request);

    // 複雑な関連ロジックの処理（顧客のスマートリンク）
    handleCustomerLinking(request, order);

    // 関連 ID の割り当て（存在確認のうえ）
    if (!castRepository.existsById(request.getCastId())) {
      throw new NotFoundException("キャストが見つかりません: " + request.getCastId());
    }
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

    // 未確定（CREATED）の申請に限り、状態遷移の入口は確定・謝絶の専用操作ただ一つ。汎用更新でも
    // 遷移できると、確定時の指名再検証・顧客の補完・謝絶の対象判定をすべて迂回できてしまう。
    // 確定後は通常の受注としてのライフサイクル（完了・キャンセル）を汎用更新が引き続き受け持つ —
    // 受付経路と申請者スナップショットは追跡のため残り続けるので、申請かどうかだけで判定してはならない。
    if (request.getStatus() != null
        && order.isReservationRequest()
        && order.getStatus() == OrderStatus.CREATED) {
      throw new ServiceException("予約申請の状態は確定・謝絶の操作でのみ変更できます");
    }

    // 非nullフィールドのみをドメインの部分更新コマンドとして適用
    order.apply(orderMapper.toPatch(request));

    // 関連 ID の更新（存在確認のうえ）
    validateReceptionist(request.getReceptionistId());
    order.assignReceptionist(request.getReceptionistId());
    if (!castRepository.existsById(request.getCastId())) {
      throw new NotFoundException("キャストが見つかりません: " + request.getCastId());
    }
    order.assignCast(request.getCastId());

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

    if (request.getReceptionistId() != null) {
      validateReceptionist(request.getReceptionistId());
    }
    order.assignReceptionist(request.getReceptionistId());

    order.assignCast(request.getCastId());
    if (order.getCastId() != null) {
      nominatableCast(order)
          .orElseThrow(() -> new NotFoundException("キャストが見つかりません: " + order.getCastId()));
    }

    order.reviseRequest(request.getPax(), request.getRemarks());

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
    nominatableCast(order)
        .orElseThrow(() -> new ServiceException("指名キャストが在籍中でないため確定できません。内容を修正するか謝絶してください"));
    if (!confirmedShiftLookupService.hasConfirmedShift(
        order.getStoreId(), order.getCastId(), order.getBusinessDate())) {
      throw new ServiceException("指名キャストにこの日の確定シフトが無いため確定できません。内容を修正するか謝絶してください");
    }
  }

  /**
   * 指名先として成立するキャスト（受注と同じ店舗に在籍する在籍中のキャスト）を引く。
   *
   * <p>店舗の一致を述語に置くのは、キャストの読み取りに掛かる絞り込みへ暗黙に頼らないため。編集時の指名先と確定時の再検証が 同じ条件を共有する。当日の確定シフトの有無は確定時だけが見る —
   * 先の日付の申請は編集時点でシフトが確定していないのが通常で、 編集で要求すると指名を差し替える手段が事実上無くなる。
   */
  private Optional<Cast> nominatableCast(Order order) {
    return castRepository
        .findById(order.getCastId())
        .filter(cast -> order.getStoreId().equals(cast.getStoreId()))
        .filter(cast -> ACTIVE_CAST_STATUS.equals(cast.getStatus()));
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
