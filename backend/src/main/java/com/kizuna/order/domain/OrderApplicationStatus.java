package com.kizuna.order.domain;

/**
 * 予約申請のステータス。PENDING から CONFIRMED / DECLINED / WITHDRAWN のいずれかへ一度だけ動き、終端に入った申請行は以後動かない
 * （申請原文は確定内容との対照先として不変のまま残る。ADR 0017）。
 *
 * <p>失効は状態として持たない — 希望日を過ぎた PENDING を導出で失効扱いする（バッチ無し）。
 */
public enum OrderApplicationStatus {
  PENDING,
  CONFIRMED,
  DECLINED,
  WITHDRAWN;

  /** 終端か。PENDING 以外はすべて終端で、確定・謝絶・取り下げのどの操作も受け付けない。 */
  public boolean isTerminal() {
    return this != PENDING;
  }
}
