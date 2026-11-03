package com.kizuna.auth.application;

/** LINE 登録の確定命令。登録チケットは 1 度きりの資格情報なので toString から伏せる。 */
public record LineRegistrationCommand(String registrationTicket, String displayName, String email) {

  @Override
  public String toString() {
    return "LineRegistrationCommand[registrationTicket=***, displayName="
        + displayName
        + ", email="
        + email
        + "]";
  }
}
