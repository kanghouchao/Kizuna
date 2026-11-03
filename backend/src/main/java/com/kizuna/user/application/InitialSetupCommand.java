package com.kizuna.user.application;

/** 最初の管理者の登録命令。toString は {@link StaffCommand.Create} と同じ理由でパスワードを伏せる。 */
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
