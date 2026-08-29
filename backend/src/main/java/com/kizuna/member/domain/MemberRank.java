package com.kizuna.member.domain;

/**
 * 会員ランク。宣言順がそのまま等級の順序で、後ろほど上位。
 *
 * <p>下位から始めて上位へしか動かない（棘輪）。昇格の指標は取消仕訳の控除で減りうるが、ランク自体は戻らない。
 */
public enum MemberRank {
  BRONZE,
  SILVER,
  GOLD;

  /** この等級が相手より上位か。 */
  public boolean isAbove(MemberRank other) {
    return ordinal() > other.ordinal();
  }
}
