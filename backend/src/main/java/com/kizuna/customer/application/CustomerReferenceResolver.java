package com.kizuna.customer.application;

import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreScopeExempt;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員の関連・申請から顧客参照を作る際、統合先を解決して書き込みまで行ロックを保持する。 ロックは既存の永続化文脈を更新しないため、統合先の判定はロック後の別問い合わせで行う。
 * 店舗で人が明示選択した顧客は追従せず、受注側で競合または選び直しを返す。
 */
@Service
@RequiredArgsConstructor
@NamedInterface("application")
public class CustomerReferenceResolver {

  /** 統合の飛行中に参照を作ろうとした場合の案内。やり直せば統合の確定した後の存続行に着く。 */
  private static final String MERGE_IN_FLIGHT = "統合中の顧客です。統合の完了後にやり直してください";

  private final CustomerRepository customerRepository;

  /**
   * 顧客参照の書き込み先。押さえられない顧客（不在・他店舗）は 404 で、存在の有無は漏れない。 墓標を渡されたら統合先へ向け直す — 参照が着くのは常に生きている行である（ADR
   * 0010）。
   *
   * <p>呼出側のトランザクションに必ず参加する（{@code MANDATORY}）。自分でトランザクションを開くと新しい Session になり、 呼出側の
   * {@code @StoreScoped} が有効にした storeFilter が掛からないまま他店舗の行を押さえたうえ、 呼出側が書き込む前に行ロックを手放してしまう。
   *
   * <p>押さえるのは着地する行だけで、統合先の下見はロックを取らずに読む。墓標を押さえたまま統合先を待つと、 その統合先を更に統合する要求と待ちが環になる — 統合は 2
   * 行を押さえた後に墓標の圧平（＝墓標の行の更新）へ進むので、 こちらが墓標を持ったまま統合先を待つと双方が相手の行を待つ。
   */
  @StoreScopeExempt(
      reason = "呼出元のトランザクションに必ず参加し（MANDATORY）、店舗境界は呼出元の storeFilter か呼出元が明示する storeId が引く")
  @Transactional(propagation = Propagation.MANDATORY)
  public String resolveForWrite(String customerId) {
    String target = customerRepository.findMergedIntoId(customerId).orElse(customerId);
    lock(target);
    Optional<String> movedWhileWaiting = customerRepository.findMergedIntoId(target);
    if (movedWhileWaiting.isEmpty()) {
      return target;
    }
    // 下見から押さえるまでの間に、その行を被統合行とする統合が確定した。追う先は既に押さえた行の
    // 統合先なので、ここから先は待たずに取る（待つと上の環がそのまま生まれる）。
    return lockWithoutWaiting(movedWhileWaiting.get());
  }

  private void lock(String customerId) {
    customerRepository
        .findByIdForUpdate(customerId)
        .orElseThrow(() -> new NotFoundException("顧客が見つかりません"));
  }

  /** 待たずに取れなければ競合として返す。取れた行が更に統合済みなら、追い続けずに同じ競合として返す（環を作らない）。 */
  private String lockWithoutWaiting(String customerId) {
    try {
      customerRepository
          .findByIdForUpdateNoWait(customerId)
          .orElseThrow(() -> new NotFoundException("顧客が見つかりません"));
    } catch (CannotAcquireLockException contended) {
      throw new ConflictException(MERGE_IN_FLIGHT);
    }
    if (customerRepository.isMerged(customerId)) {
      throw new ConflictException(MERGE_IN_FLIGHT);
    }
    return customerId;
  }
}
