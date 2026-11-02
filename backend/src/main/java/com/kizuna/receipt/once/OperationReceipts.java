package com.kizuna.receipt.once;

import com.kizuna.receipt.domain.OperationReceipt;
import com.kizuna.receipt.domain.OperationReceiptRepository;
import com.kizuna.shared.exception.ConflictException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 操作の受領（ADR 0037）。呼出側の application service のトランザクションに参加し、受領と業務更新を同時に確定・取消する。
 * キーの助言ロックをトランザクションの最初のロックとして取り、業務行のロックは work の中で呼出側が取る。
 */
@Service
@RequiredArgsConstructor
public class OperationReceipts {
  // 助言ロックの名前空間。二引数形の第一引数で、他の用途の助言ロックとは衝突しない。
  private static final int LOCK_NAMESPACE = 391;

  private final OperationReceiptRepository receipts;
  private final EntityManager em;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public <R> R once(
      ReceiptKey key, String fingerprint, Supplier<Committed> work, Function<Receipt, R> respond) {
    lock(key);
    var stored = find(key);
    if (stored != null) return respond.apply(replay(stored, fingerprint));
    var committed = work.get();
    var receipt =
        receipts.saveAndFlush(
            OperationReceipt.record(
                key.storeId(),
                key.actorId(),
                key.operationKey(),
                fingerprint,
                committed.operation(),
                committed.targetId(),
                committed.facts(),
                OffsetDateTime.now(clock)));
    return respond.apply(view(receipt, false));
  }

  /** 受領キーの一意制約に敗れた要求を、別トランザクションで既存の受領として解決する。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public <R> R replayOnly(ReceiptKey key, String fingerprint, Function<Receipt, R> respond) {
    var stored = find(key);
    // 一意制約の違反は競合相手の確定後にしか起きないので、受領が無いのは不変条件の破れである。
    if (stored == null) throw new IllegalStateException("競合した操作の受領が見つかりません");
    return respond.apply(replay(stored, fingerprint));
  }

  private void lock(ReceiptKey key) {
    // 衝突した hash は無関係なキーを直列化するだけで、同一性は一意制約と照会が保証する。
    em.createNativeQuery("select pg_advisory_xact_lock(:namespace, :key)", Object.class)
        .setParameter("namespace", LOCK_NAMESPACE)
        .setParameter(
            "key", (key.storeId() + ":" + key.actorId() + ":" + key.operationKey()).hashCode())
        .getSingleResult();
  }

  private OperationReceipt find(ReceiptKey key) {
    return receipts
        .findByStoreIdAndActorIdAndOperationKey(key.storeId(), key.actorId(), key.operationKey())
        .orElse(null);
  }

  private static Receipt replay(OperationReceipt stored, String fingerprint) {
    if (!stored.getFingerprint().equals(fingerprint))
      throw new ConflictException("同じ要求キーに異なる操作が指定されています");
    return view(stored, true);
  }

  private static Receipt view(OperationReceipt receipt, boolean replayed) {
    return new Receipt(
        receipt.getId(),
        receipt.getOperation(),
        receipt.getTargetId(),
        receipt.getCommittedFacts(),
        replayed);
  }
}
