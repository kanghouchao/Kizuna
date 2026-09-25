package com.kizuna.customer.application;

import com.kizuna.customer.api.dto.CustomerMergeAuditResponse;
import com.kizuna.customer.api.dto.CustomerMergeHistoryResponse;
import com.kizuna.customer.api.dto.CustomerMergePreviewRequest;
import com.kizuna.customer.api.dto.CustomerMergePreviewResponse;
import com.kizuna.customer.api.dto.CustomerMergeRequest;
import com.kizuna.customer.api.dto.CustomerMergeResponse;
import com.kizuna.customer.api.dto.MergeDirection;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMerge;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerMergeView;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.customer.domain.MergeEvidence;
import com.kizuna.customer.domain.MergeSnapshot;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.domain.PlatformUserRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 同一店舗の顧客資料を確定し、全受注・連絡先・会員関連を付け替えて原資料と確定資料を監査に残す。 被統合行は墓標として残し、連鎖統合は旧 ID が常に一跳で解決できるよう圧平する。
 * ポイント残高は確認のために読むだけで、台帳・受注帰属記録・会員の来店履歴は書き換えない。 統合は取消不能であり、誤統合の修復は監査記録を根拠とする人手作業になる。
 */
@Service
@RequiredArgsConstructor
public class CustomerMergeService {

  /** 両行が認領されているときの案内。事前判定と、それをすり抜けた競合が部分一意索引に当たる場合とで同じ文言を返す。 */
  private static final String RELEASE_THE_LINK_FIRST = "両方の顧客に会員が紐づいています。先に関連を解除してから統合してください";

  private final EntityManager entityManager;
  private final MergePreparation preparation;
  private final MergeConfirmation confirmation;

  private final CustomerRepository customerRepository;
  private final CustomerContactService customerContactService;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final CustomerMergeRepository customerMergeRepository;
  private final PlatformUserRepository platformUserRepository;
  private final StoreContext storeContext;

  /** 墓標は別の顧客へ読み替えず拒否する。確認した二行と原資料の一致が不可逆な統合の前提になる。 */
  @StoreScoped
  @Transactional
  public CustomerMergeResponse merge(
      String survivingCustomerId, CustomerMergeRequest request, String actorEmail) {
    String mergedCustomerId = request.mergedCustomerId();
    if (survivingCustomerId.equals(mergedCustomerId)) {
      throw new ServiceException("同じ顧客を統合することはできません");
    }
    Long actorId = resolveActorId(actorEmail);
    Customer merged = lockBothInIdOrder(survivingCustomerId, mergedCustomerId);

    // 可否は実行の瞬間に判定し直す。確認画面を開いた時点の判定は、その後の統合で古くなりうる。
    rejectTombstones(survivingCustomerId, mergedCustomerId);
    if (hasActiveLink(survivingCustomerId) && hasActiveLink(mergedCustomerId)) {
      // 部分一意索引により両者は必ず別会員を指す。二人の本人がそれぞれ認領している行なので、
      // 台帳級の「同一人物か」の判断と一緒に片付けさせない（ADR 0010）。
      throw new ConflictException(RELEASE_THE_LINK_FIRST);
    }

    var prepared = preparation.prepare(survivingCustomerId, request.preview());
    confirmation.verify(request.previewToken(), prepared.response().previewToken());

    var preview = prepared.response();
    customerRepository
        .findById(survivingCustomerId)
        .orElseThrow()
        .adoptMergeProfile(preview.profile());
    customerContactService.transfer(
        survivingCustomerId, mergedCustomerId, actorId, preview.preferredContacts());
    Long storeId = storeContext.getStoreId();
    int movedOrderCount =
        customerMergeRepository.repointOrders(survivingCustomerId, mergedCustomerId, storeId);
    int movedLinkCount = repointLinks(survivingCustomerId, mergedCustomerId, storeId);
    customerRepository.flattenMergedInto(survivingCustomerId, mergedCustomerId, storeId);

    merged.mergeInto(survivingCustomerId);
    customerRepository.save(merged);
    entityManager.flush();
    // 一括付替え後の関連を第一次キャッシュから読むと旧顧客 ID が残るため、監査は再読する。
    entityManager.clear();
    var evidence =
        new MergeEvidence(
            preview.surviving(),
            preview.merged(),
            preparation.snapshot(survivingCustomerId),
            prepared.movedOrderIds(),
            preview.merged().contacts().stream().map(MergeSnapshot.Contact::id).toList(),
            preview.merged().memberLinks().stream().map(MergeSnapshot.Link::id).toList(),
            actorId,
            platformUserRepository
                .findById(actorId)
                .map(user -> user.getDisplayName())
                .orElse(null));
    var recorded =
        customerMergeRepository.save(
            CustomerMerge.record(
                survivingCustomerId,
                mergedCustomerId,
                actorId,
                OffsetDateTime.now(),
                movedOrderCount,
                movedLinkCount,
                preview.movedContactCount(),
                request.operationReason().strip(),
                evidence));
    return new CustomerMergeResponse(
        survivingCustomerId,
        movedOrderCount,
        movedLinkCount,
        recorded.getId(),
        preview.movedContactCount());
  }

  @StoreScoped
  @Transactional
  public CustomerMergePreviewResponse preview(
      String survivingId, CustomerMergePreviewRequest input) {
    if (survivingId.equals(input.mergedCustomerId()))
      throw new ServiceException("同じ顧客を統合することはできません");
    lockBothInIdOrder(survivingId, input.mergedCustomerId());
    rejectTombstones(survivingId, input.mergedCustomerId());
    return preparation.prepare(survivingId, input).response();
  }

  /**
   * 顧客 1 件の統合履歴。存続行として受けた統合と、自分が被統合となった統合の両方を新しい順に返す。続きはカーソルで辿る。
   *
   * <p>統合を 1 件も持たない顧客は空で返る。404 にしないのは、「統合が無い」と「読めなかった」を呼出側が区別できるようにするため — 404 は顧客そのものが引けないときだけである。
   *
   * <p>統合履歴は機微情報なので、読むにも実行と同じ {@code CUSTOMER_MERGE} を要する（強制は端点の注釈）。
   *
   * @param cursor 続きの位置。null なら先頭から
   * @param requestedSize 1 回に返す件数の希望値（上限に丸められる）
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<CustomerMergeHistoryResponse> history(
      String customerId, String cursor, int requestedSize) {
    if (!customerRepository.existsById(customerId)) {
      throw new NotFoundException("顧客が見つかりません");
    }
    int size = CursorPage.clampSize(requestedSize);
    // 続きの有無は上限より 1 件多く取って判る。総件数の問い合わせを毎回撒かずに済む。
    Limit limit = Limit.of(size + 1);
    List<CustomerMergeView> fetched =
        cursor == null
            ? customerMergeRepository.findHistory(customerId, limit)
            : fetchHistoryAfter(customerId, PageCursor.decode(cursor), limit);
    return CursorPage.of(fetched, size, CustomerMergeService::cursorOf)
        .map(view -> toHistoryResponse(customerId, view));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CustomerMergeAuditResponse audit(String customerId, String mergeId) {
    var merge =
        customerMergeRepository
            .findById(mergeId)
            .filter(
                m ->
                    m.getSurvivingCustomerId().equals(customerId)
                        || m.getMergedCustomerId().equals(customerId))
            .orElseThrow(() -> new NotFoundException("統合履歴が見つかりません"));
    return CustomerMergeAuditResponse.from(merge);
  }

  private List<CustomerMergeView> fetchHistoryAfter(
      String customerId, PageCursor cursor, Limit limit) {
    return customerMergeRepository.findHistoryAfter(
        customerId, cursor.timestampKey(), cursor.id(), limit);
  }

  /** 続きの位置は一覧の並び（統合時刻 + id）と同じ組で作る。組が並びとずれると、続きが手前へ戻るか行を飛ばす。 */
  private static String cursorOf(CustomerMergeView view) {
    return new PageCursor(view.getMergedAt().toString(), view.getId()).encode();
  }

  /** 「相手」は問い合わせた顧客によって変わる。向きの判定を記録の側に持たせず、読み手ごとにここで解く。 */
  private static CustomerMergeHistoryResponse toHistoryResponse(
      String customerId, CustomerMergeView view) {
    boolean surviving = customerId.equals(view.getSurvivingCustomerId());
    return new CustomerMergeHistoryResponse(
        view.getId(),
        surviving ? MergeDirection.SURVIVING : MergeDirection.MERGED,
        surviving ? view.getMergedCustomerId() : view.getSurvivingCustomerId(),
        surviving ? view.getMergedCustomerName() : view.getSurvivingCustomerName(),
        view.getMergedByName(),
        view.getMergedAt(),
        view.getMovedOrderCount(),
        view.getMovedLinkCount(),
        view.getMovedContactCount(),
        view.getOperationReason());
  }

  /**
   * 2 行を顧客 ID の昇順で悲観排他ロックし、被統合行の実体を返す。取得できない顧客は他店舗の顧客も含めて 404 で、存在の有無は漏れない。
   *
   * <p>昇順に固定するのは、同じ 2 行を逆向きに統合しようとする要求どうしがデッドロックしないため。統合は台帳の加算ロットを触らないので、 既存のロック順序（顧客行 → 関連 →
   * 台帳の加算ロット）と待ちが環にならない。
   */
  private Customer lockBothInIdOrder(String survivingCustomerId, String mergedCustomerId) {
    boolean survivingFirst = survivingCustomerId.compareTo(mergedCustomerId) < 0;
    Customer first = lockCustomer(survivingFirst ? survivingCustomerId : mergedCustomerId);
    // 2 本目を待つ前に、墓標を名指した要求（再送・墓標を存続行に指定）を撥ねる。参照の解決は
    // 墓標 → 統合先の順に押さえるので、ここで待つと ID 昇順と逆向きの待ちが環になりうる。
    // 墓標は取消が無く元に戻らないため、ロック前の読みで撥ねても判定を誤らない。
    rejectTombstones(survivingCustomerId, mergedCustomerId);
    Customer second = lockCustomer(survivingFirst ? mergedCustomerId : survivingCustomerId);
    return survivingFirst ? second : first;
  }

  /** 判定は押さえた実体の状態からではなく別問い合わせで行う（{@code CustomerRepository#isMerged} の契約）。 */
  private void rejectTombstones(String survivingCustomerId, String mergedCustomerId) {
    if (customerRepository.isMerged(survivingCustomerId)) {
      throw new ConflictException("統合先の顧客は既に統合済みです。統合先の顧客を指定してください");
    }
    if (customerRepository.isMerged(mergedCustomerId)) {
      throw new ConflictException("この顧客は既に統合済みです");
    }
  }

  private Customer lockCustomer(String customerId) {
    return customerRepository
        .findByIdForUpdate(customerId)
        .orElseThrow(() -> new NotFoundException("顧客が見つかりません"));
  }

  private boolean hasActiveLink(String customerId) {
    return customerMemberLinkRepository.existsByCustomerIdAndStatus(customerId, LinkStatus.ACTIVE);
  }

  /**
   * 関連を存続行へ付け替える。
   *
   * <p>両行の ACTIVE 関連は上で撥ねているが、部分一意索引 {@code uq_t_customer_member_links_active_customer}
   * が最終防波堤として残る。事前判定をすり抜けた競合はここで違反として現れるので、汎用の一意違反文言ではなく 次の一手の読める案内へ写す。
   */
  private int repointLinks(String survivingCustomerId, String mergedCustomerId, Long storeId) {
    try {
      return customerMemberLinkRepository.repointCustomer(
          survivingCustomerId, mergedCustomerId, storeId);
    } catch (DataIntegrityViolationException ex) {
      throw IntegrityViolations.translate(
          ex,
          Map.of(
              DbConstraint.UQ_T_CUSTOMER_MEMBER_LINKS_ACTIVE_CUSTOMER,
              () -> new ConflictException(RELEASE_THE_LINK_FIRST)));
    }
  }

  /** JWT は user-id claim を持たないため、実行者は認証主体の email から解決する。 */
  private Long resolveActorId(String actorEmail) {
    return platformUserRepository
        .findByEmail(actorEmail)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }
}
