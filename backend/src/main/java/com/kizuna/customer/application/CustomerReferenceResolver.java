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
 * 顧客参照を書く経路が、書き込み先の顧客 ID を得る唯一の口。
 *
 * <p>顧客参照を書くのは 5 経路 — 受注録入の顧客 ID 指定・受注録入の電話照合で 1 件一致した行・会員ポータルからの予約申請・会員申請の確定時自動整備・会員紐づけの成立。
 * 既にある行を指す参照はどれもここを通ることで「書く直前に対象の顧客行を悲観排他ロックする」規律を共有する。経路ごとに解決を持つ形だと、 次の経路が増えたときに直列化の抜けが静かに再び開く。
 *
 * <p>同じ経路でも、その場で起こした行へ着けるとき（電話照合の 0 件・自動整備で台帳行を新設するとき）は通らない —
 * 起こした行は同じトランザクションの外から見えず、他の経路の書き換えに晒されていない。
 *
 * <p>ロックが何と直列化するか・ロック順序・店舗境界の契約は {@link CustomerRepository#findByIdForUpdate} に記す。
 *
 * <p>押さえた行から実体の状態は読まない。悲観排他ロックは既に永続化文脈に載っている実体の状態を更新しないため、
 * ロック後に実体のフィールドを読むと第一次キャッシュの古い値を見る。行に基づく判断が要るときも、ロックの後の別問い合わせで読む。
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
