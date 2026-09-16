package com.kizuna.service.application;

/** 本人拒否の確定に必要な受注反映。呼出元と同じトランザクションで実行し、失敗は呼出元へ返す。 */
public interface SpecialServiceRejectionHandler {
  void applyRejection(SpecialServiceRejection rejection);
}
