package com.kizuna.order.application;

import com.kizuna.cast.domain.Cast;
import com.kizuna.customer.application.CustomerReferenceResolver;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.order.api.dto.MemberOrderCreateRequest;
import com.kizuna.order.api.dto.MemberOrderResponse;
import com.kizuna.order.domain.MemberOrderView;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.ReceptionRoute;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.shift.application.ConfirmedShiftLookupService;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員ポータルからの予約申請ユースケース。
 *
 * <p>会員は店舗を授権されないため店舗文脈（{@code @StoreScoped}）を確立できず、storeFilter も働かない。したがって
 *
 * <ul>
 *   <li>書き込みは店舗の実在を確かめたうえで store_id を明示設定する（{@code StoreScopeStampListener} は設定済みの store_id を尊重する）
 *   <li>読み取り・取り消しは申請者（requesterMemberId）の一致が唯一の隔離境界であり、必ず問い合わせ自体に載せる
 * </ul>
 *
 * <p>申請できる店舗は絞らない（紐づけ済みの店舗に限らない）。会員が初めて訪れる店舗にも申請できることが業務上の既定で、顧客台帳との紐づけは店舗側の操作で後から成立する。
 */
@Service
@RequiredArgsConstructor
public class MemberOrderService {

  private final OrderRepository orderRepository;
  private final NominatableCastLookup nominatableCast;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final CustomerReferenceResolver customerReferenceResolver;
  private final PlatformUserRepository platformUserRepository;
  private final MemberLookupService memberLookupService;
  private final ConfirmedShiftLookupService confirmedShiftLookupService;
  private final StoreExistenceCheck storeExistenceCheck;
  private final BusinessDateService businessDateService;

  /** 予約を申請する。申請は受注（Order）の CREATED として起き、店舗の確定で同じ行が CONFIRMED になる。 */
  @StoreScopeExempt(reason = "会員は店舗文脈を確立できないため、店舗の実在を確かめたうえで store_id を明示設定して書く")
  @Transactional
  public MemberOrderResponse request(String email, MemberOrderCreateRequest request) {
    MemberLookup member = resolveMember(email);
    Long storeId = request.getStoreId();
    if (!storeExistenceCheck.exists(storeId)) {
      throw new ServiceException("店舗が見つかりません");
    }
    validateBusinessDate(request.getBusinessDate());
    validateNomination(storeId, request.getCastId(), request.getBusinessDate());

    Order order =
        Order.builder()
            .businessDate(request.getBusinessDate())
            .arrivalScheduledStartTime(request.getArrivalScheduledStartTime())
            .pax(request.getPax())
            .castId(request.getCastId())
            .remarks(request.getRemarks())
            .status(OrderStatus.CREATED)
            .receptionRoute(ReceptionRoute.WEB)
            .requesterMemberId(member.memberId())
            .requesterMemberCode(member.memberCode())
            .requesterDeclaredName(request.getDeclaredName())
            .build();
    // 店舗文脈が無い経路のため store_id を明示する。
    order.setStoreId(storeId);
    resolveLinkedCustomerId(storeId, member.memberId()).ifPresent(order::linkCustomer);

    Order saved = orderRepository.save(order);
    return toResponse(member.memberId(), saved.getId());
  }

  /**
   * 本人が申請した予約の一覧（跨店集約）。
   *
   * <p>続きの指定は inbox と同じくカーソル（並びの鍵）で受ける。取り下げで行が消えることはないが、確定・謝絶の反映で
   * 並びが動きうる一覧を件数で指すと、続きを取った時点で境界の予約を飛ばす。
   *
   * @param cursor 続きの位置。null なら先頭から
   * @param requestedSize 1 回に返す件数の希望値（上限に丸められる）
   */
  @StoreScopeExempt(reason = "会員は店舗文脈を確立できないため、申請者（requesterMemberId）の一致を問い合わせ自体に載せることが唯一の境界")
  @Transactional(readOnly = true)
  public CursorPage<MemberOrderResponse> list(String email, String cursor, int requestedSize) {
    MemberLookup member = resolveMember(email);
    int size = CursorPage.clampSize(requestedSize);
    // 続きの有無は上限より 1 件多く取って判る。総件数の問い合わせを毎回撒かずに済む。
    Limit limit = Limit.of(size + 1);
    List<MemberOrderView> fetched =
        cursor == null
            ? orderRepository.findMemberViews(member.memberId(), limit)
            : fetchAfter(member.memberId(), PageCursor.decode(cursor), limit);
    return CursorPage.of(fetched, size, MemberOrderService::cursorOf).map(this::toResponse);
  }

  private List<MemberOrderView> fetchAfter(Long memberId, PageCursor cursor, Limit limit) {
    // 続きの取得でも申請者の一致は問い合わせに載せ続ける — カーソルは位置を指すだけで、隔離境界にはならない。
    return orderRepository.findMemberViewsAfter(memberId, cursor.dateKey(), cursor.id(), limit);
  }

  /** 続きの位置は一覧の並び（業務日 + id）と同じ組で作る。組が並びとずれると、続きが手前へ戻るか行を飛ばす。 */
  private static String cursorOf(MemberOrderView view) {
    return new PageCursor(view.getBusinessDate().toString(), view.getId()).encode();
  }

  /** 本人が申請した未確定の予約を取り下げる。確定後は店舗との調整が要るため取り下げられない。 */
  @StoreScopeExempt(reason = "会員は店舗文脈を確立できないため、申請者（requesterMemberId）の一致を取り出しの条件に載せることが唯一の境界")
  @Transactional
  public MemberOrderResponse cancel(String email, String orderId) {
    MemberLookup member = resolveMember(email);
    Order order = ownedOrder(member.memberId(), orderId);
    order.cancelRequest();
    orderRepository.save(order);
    return toResponse(member.memberId(), orderId);
  }

  /** 本人が申請した予約を取り出す。他人の予約・店舗が起こした受注は「見つからない」として扱う — 権限違反として区別すると、その id の予約が存在することそのものが分かってしまう。 */
  private Order ownedOrder(Long memberId, String orderId) {
    return orderRepository
        .findById(orderId)
        .filter(order -> memberId.equals(order.getRequesterMemberId()))
        .orElseThrow(() -> new NotFoundException("予約が見つかりません: " + orderId));
  }

  private MemberLookup resolveMember(String email) {
    Long platformUserId =
        platformUserRepository
            .findByEmail(email)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
            .getId();
    return memberLookupService
        .findByPlatformUserId(platformUserId)
        .orElseThrow(() -> new StaleSessionException("会員情報が存在しません"));
  }

  /** 利用日は「現在の営業日以降かつ候補照会と同じ上限（90 日）以内」。指名なしの申請が候補照会を経ずに上限を素通りしないよう、書き込み側でも同じ範囲を見る。 */
  private void validateBusinessDate(LocalDate businessDate) {
    LocalDate today = businessDateService.currentBusinessDate();
    if (businessDate.isBefore(today)) {
      throw new ServiceException("過去の日付は申請できません");
    }
    if (ChronoUnit.DAYS.between(today, businessDate)
        > ConfirmedShiftLookupService.MAX_LOOKAHEAD_DAYS) {
      throw new ServiceException("申請できる日付の範囲を超えています");
    }
  }

  /**
   * 指名は「その店舗に在籍中のキャスト」かつ「当日の確定シフトに入っていること」を満たす場合のみ受け付ける。
   *
   * <p>在籍状態も公開可否も候補一覧と同じ条件で書き込み側でも見る — 候補に出さないだけでは、キャスト ID を直接送る要求を防げない。
   *
   * <p>対象は会員なので、成立しない理由を区別せず「見つからない」として返す — 区別すると、その id のキャストが当該店舗に在籍することそのものが分かってしまう。店舗スタッフ向けの
   * {@link OrderService} は同じ述語から 400 を返す。
   */
  private void validateNomination(Long storeId, String castId, LocalDate businessDate) {
    if (castId == null) {
      return;
    }
    Cast cast =
        nominatableCast
            .find(storeId, castId)
            .orElseThrow(() -> new NotFoundException("キャストが見つかりません: " + castId));
    // 失敗の文言を非公開かどうかで分けない — 分けた瞬間、隠したはずのシフトの存在が会員に読み取れる。
    if (!confirmedShiftLookupService.hasPubliclyVisibleShift(storeId, cast.getId(), businessDate)) {
      throw new ServiceException("指名したキャストはこの日の出勤予定がありません");
    }
  }

  /** 申請先店舗に本人の紐づけ済み顧客があれば結び付ける。無ければ空のままで、店舗側の紐づけ操作で後から成立する。 */
  /**
   * 申請先店舗に申請者の有効な関連があれば、その顧客を受注の着き先として解決する。関連が無ければ空（顧客未設定の申請は正規の状態で、確定時に整備される）。
   *
   * <p>解決は顧客参照を書く他の経路と同じ口を通し、書く直前に顧客行を押さえる。関連の照会はロックを取らないため、 ここを通さないと台帳側の書き換えと何も競合せずに参照だけが決まる。
   *
   * <p>この経路は店舗文脈を確立できず storeFilter が働かないので、店舗境界は解決口ではなく関連の照会（storeId を明示）が引く。 解決口が引けない顧客は 404
   * になるが、関連の顧客参照には外部キーがあるためこの経路では起こらない。
   */
  private Optional<String> resolveLinkedCustomerId(Long storeId, Long memberId) {
    return customerMemberLinkRepository
        .findByStoreIdAndMemberIdAndStatus(storeId, memberId, LinkStatus.ACTIVE)
        .map(CustomerMemberLink::getCustomerId)
        .map(customerReferenceResolver::resolveForWrite);
  }

  private MemberOrderResponse toResponse(Long memberId, String orderId) {
    return orderRepository
        .findMemberView(memberId, orderId)
        .map(this::toResponse)
        .orElseThrow(() -> new NotFoundException("予約が見つかりません: " + orderId));
  }

  private MemberOrderResponse toResponse(MemberOrderView view) {
    return MemberOrderResponse.builder()
        .id(view.getId())
        .storeId(view.getStoreId())
        .storeName(view.getStoreName())
        .businessDate(view.getBusinessDate())
        .arrivalScheduledStartTime(view.getArrivalScheduledStartTime())
        .pax(view.getPax())
        .castName(view.getCastName())
        .status(view.getStatus() == null ? null : view.getStatus().name())
        .build();
  }
}
