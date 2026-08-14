package com.kizuna.customer.application;

import com.kizuna.customer.api.dto.CustomerMergeResponse;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMerge;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 同一店舗内で重複した顧客行を一つへまとめるユースケース。形は付替え＋墓標＋統合履歴で、行は削除しない（ADR 0010）。
 *
 * <p>存続行へ被統合行の受注（全状態）と関連（ACTIVE・RELEASED の全区間）を付け替え、被統合行は統合先参照を持つ墓標として残す。 連鎖統合は圧平する — B を C
 * へ統合するとき、B を指していた既存の墓標もすべて C へ付け替え、旧 ID の解決が常に一跳で届くようにする。
 *
 * <p>統合はポイント台帳・受注帰属記録・会員の来店履歴に一切波及しない。これは偶然ではなく構造的である — 仕訳も帰属記録も受注と会員を 参照し、顧客を参照しないため（ADR 0006 /
 * 0009）、受注の顧客参照を書き換えてもそれらの行は読まれも書かれもしない。
 *
 * <p>統合に取消（undo）は無い。誤統合の修復は統合履歴を根拠とする人手作業であり、この操作が店長権限（{@code CUSTOMER_MERGE}）に限られるのはそのためである。
 */
@Service
@RequiredArgsConstructor
public class CustomerMergeService {

  /** 両行が認領されているときの案内。事前判定と、それをすり抜けた競合が部分一意索引に当たる場合とで同じ文言を返す。 */
  private static final String RELEASE_THE_LINK_FIRST = "両方の顧客に会員が紐づいています。先に関連を解除してから統合してください";

  private final CustomerRepository customerRepository;
  private final CustomerMemberLinkRepository customerMemberLinkRepository;
  private final CustomerMergeRepository customerMergeRepository;
  private final PlatformUserRepository platformUserRepository;
  private final StoreContext storeContext;

  /**
   * 統合を実行する。
   *
   * <p>顧客参照を書く他の経路（{@link CustomerReferenceResolver}）は通さない。あちらは「既にある行を指す参照の書き込み先」を解決する口で、
   * 墓標を渡されれば統合先へ 向け直す。統合が対象にするのは名指された 2 行そのもので、別の行へ向け直してよい操作ではない（解除 {@code
   * CustomerMemberLinkService#unlink} と同じ理由）。
   *
   * @param survivingCustomerId 統合後に台帳へ残る行
   * @param mergedCustomerId 墓標になる行
   */
  @StoreScoped
  @Transactional
  public CustomerMergeResponse merge(
      String survivingCustomerId, String mergedCustomerId, String actorEmail) {
    if (survivingCustomerId.equals(mergedCustomerId)) {
      throw new ServiceException("同じ顧客を統合することはできません");
    }
    Long actorId = resolveActorId(actorEmail);
    Customer merged = lockBothInIdOrder(survivingCustomerId, mergedCustomerId);

    // 可否は実行の瞬間に判定し直す。確認画面を開いた時点の判定は、その後の統合で古くなりうる。
    // 判定は押さえた実体の状態からではなく別問い合わせで行う（CustomerRepository#isMerged の契約）。
    if (customerRepository.isMerged(survivingCustomerId)) {
      throw new ConflictException("統合先の顧客は既に統合済みです。統合先の顧客を指定してください");
    }
    if (customerRepository.isMerged(mergedCustomerId)) {
      throw new ConflictException("この顧客は既に統合済みです");
    }
    if (hasActiveLink(survivingCustomerId) && hasActiveLink(mergedCustomerId)) {
      // 部分一意索引により両者は必ず別会員を指す。二人の本人がそれぞれ認領している行なので、
      // 台帳級の「同一人物か」の判断と一緒に片付けさせない（ADR 0010）。
      throw new ConflictException(RELEASE_THE_LINK_FIRST);
    }

    Long storeId = storeContext.getStoreId();
    int movedOrderCount =
        customerMergeRepository.repointOrders(survivingCustomerId, mergedCustomerId, storeId);
    int movedLinkCount = repointLinks(survivingCustomerId, mergedCustomerId, storeId);
    customerRepository.flattenMergedInto(survivingCustomerId, mergedCustomerId, storeId);

    merged.mergeInto(survivingCustomerId);
    customerRepository.save(merged);
    customerMergeRepository.save(
        CustomerMerge.record(
            survivingCustomerId,
            mergedCustomerId,
            actorId,
            OffsetDateTime.now(),
            movedOrderCount,
            movedLinkCount));

    return new CustomerMergeResponse(survivingCustomerId, movedOrderCount, movedLinkCount);
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
    Customer second = lockCustomer(survivingFirst ? mergedCustomerId : survivingCustomerId);
    return survivingFirst ? second : first;
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
