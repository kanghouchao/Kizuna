package com.kizuna.shift.api.dto;

import lombok.Data;

/** 出勤希望の承認要求。本体そのものが任意で、非公開で出生させたい新規希望だけが公開可否を明示する。 */
@Data
public class ShiftApprovalRequest {

  /** 承認で生まれるシフトの公開可否。省略時は公開可。変更申請（CHANGE）の承認では指定できない。 */
  private Boolean published;
}
