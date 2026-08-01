package com.kizuna.member.domain;

import java.util.random.RandomGenerator;

/**
 * 会員コードの生成規則。数字 12 桁の固定長文字列（先頭 0 を許容）で、QR 表示と口頭・手入力の両方で扱える。
 *
 * <p>一意性は本クラスでは担保しない — 発行側の重複チェックと DB の一意制約が担う。
 */
public final class MemberCodes {

  /** 会員コードの桁数。 */
  public static final int LENGTH = 12;

  private MemberCodes() {}

  /** 数字 {@value LENGTH} 桁の会員コードを生成する。 */
  public static String generate(RandomGenerator random) {
    StringBuilder code = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      code.append(random.nextInt(10));
    }
    return code.toString();
  }
}
