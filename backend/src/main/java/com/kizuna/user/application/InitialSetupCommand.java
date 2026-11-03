package com.kizuna.user.application;

/** 最初の管理者の登録命令。生のパスワードを持つため、記録型が自動生成する toString からは伏せる。 */
public record InitialSetupCommand(String email, String password, String displayName) {

  @Override
  public String toString() {
    return "InitialSetupCommand[email="
        + email
        + ", password=***, displayName="
        + displayName
        + "]";
  }
}
