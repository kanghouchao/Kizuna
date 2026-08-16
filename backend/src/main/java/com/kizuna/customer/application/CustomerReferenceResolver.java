package com.kizuna.customer.application;

import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreScopeExempt;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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

  private final CustomerRepository customerRepository;

  /**
   * 顧客参照の書き込み先。押さえられない顧客（不在・他店舗）は 404 で、存在の有無は漏れない。 墓標を渡されたら統合先へ向け直す — 参照が着くのは常に生きている行である（ADR
   * 0010）。
   *
   * <p>呼出側のトランザクションに必ず参加する（{@code MANDATORY}）。自分でトランザクションを開くと新しい Session になり、 呼出側の
   * {@code @StoreScoped} が有効にした storeFilter が掛からないまま他店舗の行を押さえたうえ、 呼出側が書き込む前に行ロックを手放してしまう。
   */
  @StoreScopeExempt(
      reason = "呼出元のトランザクションに必ず参加し（MANDATORY）、店舗境界は呼出元の storeFilter か呼出元が明示する storeId が引く")
  @Transactional(propagation = Propagation.MANDATORY)
  public String resolveForWrite(String customerId) {
    lock(customerId);
    Optional<String> survivingCustomerId = customerRepository.findMergedIntoId(customerId);
    if (survivingCustomerId.isEmpty()) {
      return customerId;
    }
    // 着地する行も押さえる。押さえないと、存続行を更に統合する要求が付替えを済ませた後で墓標の
    // 圧平を待つ間にこの書き込みが割り込み、付替えの済んだ行へ着いたまま取り残される。
    lock(survivingCustomerId.get());
    return survivingCustomerId.get();
  }

  /**
   * 追うのは一跳だけでよい。圧平により、コミット済みの状態に二段の連鎖は存在しない（ADR 0010）。
   * 存続行を統合する要求はこちらが押さえている墓標を圧平しなければ進めないので、押さえた時点で 存続行が墓標になっていることもない。
   */
  private void lock(String customerId) {
    customerRepository
        .findByIdForUpdate(customerId)
        .orElseThrow(() -> new NotFoundException("顧客が見つかりません"));
  }
}
