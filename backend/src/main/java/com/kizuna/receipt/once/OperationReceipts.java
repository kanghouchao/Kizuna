package com.kizuna.receipt.once;

import com.kizuna.receipt.domain.OperationReceipt;
import com.kizuna.receipt.domain.OperationReceiptRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ResourceBusyException;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 操作の受領（ADR 0037）。呼出側の application service のトランザクションに参加し、受領と業務更新を同時に確定・取消する。
 * キーの助言ロックをトランザクションの最初のロックとして取り、業務行のロックは work の中で呼出側が取る。
 */
@Service
public class OperationReceipts {
  // 助言ロックの名前空間。二引数形の第一引数で、他の用途の助言ロックとは衝突しない。
  private static final int LOCK_NAMESPACE = 391;

  private static final String LOCK_NOT_AVAILABLE = "55P03";

  private final OperationReceiptRepository receipts;
  private final EntityManager em;
  private final int lockTimeoutSeconds;

  public OperationReceipts(
      OperationReceiptRepository receipts,
      EntityManager em,
      @Value("${app.receipt.lock-timeout-seconds:5}") int lockTimeoutSeconds) {
    this.receipts = receipts;
    this.em = em;
    this.lockTimeoutSeconds = lockTimeoutSeconds;
  }

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
                committed.facts()));
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

  // 待ちの上限は助言ロックだけに掛け、取得後は既定へ戻して呼出側の行ロックの待ち方を変えない。
  // 打ち切りは保持者が成立するかについて何も語らないので、画面が操作を保持したまま再送する 503 にする。
  private void lock(ReceiptKey key) {
    em.createNativeQuery("set local lock_timeout = '" + lockTimeoutSeconds + "s'").executeUpdate();
    try {
      // 衝突した hash は無関係なキーを直列化するだけで、同一性は一意制約と照会が保証する。
      em.createNativeQuery("select pg_advisory_xact_lock(:namespace, :key)", Object.class)
          .setParameter("namespace", LOCK_NAMESPACE)
          .setParameter(
              "key", (key.storeId() + ":" + key.actorId() + ":" + key.operationKey()).hashCode())
          .getSingleResult();
    } catch (RuntimeException failure) {
      if (lockNotAvailable(failure)) throw new ResourceBusyException("同じ操作を処理中です。時間をおいて再試行してください");
      throw failure;
    }
    em.createNativeQuery("set local lock_timeout to default").executeUpdate();
  }

  private static boolean lockNotAvailable(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && LOCK_NOT_AVAILABLE.equals(sql.getSQLState()))
        return true;
    }
    return false;
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
