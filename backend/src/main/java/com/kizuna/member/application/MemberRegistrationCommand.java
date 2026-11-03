package com.kizuna.member.application;

/** 会員の自助登録の命令。生のパスワードを持つため、記録型が自動生成する toString からは伏せる（ログへの混入を防ぐ）。 */
public record MemberRegistrationCommand(String email, String password, String displayName) {

  @Override
  public String toString() {
    return "MemberRegistrationCommand[email="
        + email
        + ", password=***, displayName="
        + displayName
        + "]";
  }
}
