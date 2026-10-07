package com.kizuna.recruitment.infrastructure;

import com.kizuna.shared.exception.ServiceUnavailableException;

public class AttachmentStorageMissingException extends ServiceUnavailableException {
  public AttachmentStorageMissingException() {
    super("非公開添付の保存を確認できません。元の画像で再試行してください");
  }
}
