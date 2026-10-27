package com.kizuna.order.domain;

import com.kizuna.shared.exception.ConflictException;
import java.util.Map;

/** 画面が読み込んだ後に受注が更新された。画面は詳細の {@code expected_version} を契機に取り直す。 */
public class OrderVersionConflictException extends ConflictException {

  public OrderVersionConflictException() {
    super(
        "この受注は別の操作者が更新しました。最新の内容を読み直してからやり直してください", Map.of("expected_version", "最新の内容を読み直してください"));
  }
}
