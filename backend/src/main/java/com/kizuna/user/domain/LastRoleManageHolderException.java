package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/**
 * 有効な ROLE_MANAGE 実効保持者を 0 にする操作（停止・ロール剥奪・ロール定義からの ROLE_MANAGE 除去）を拒んだことを表すドメイン例外。ServiceException
 * 継承により HTTP 400 で応答される。
 */
public class LastRoleManageHolderException extends ServiceException {

  public LastRoleManageHolderException(String message) {
    super(message);
  }
}
