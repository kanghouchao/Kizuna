package com.kizuna.shared.web;

import java.beans.PropertyEditorSupport;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * クエリ・パス変数の文字列を束縛の時点で前後の空白を除き、空になったものは null にする。
 *
 * <p>「空白だけの検索語」の解釈を各 service に委ねると、同じ入力が端点ごとに違う結果を返す。値が最初に型を得る束縛層で
 * 正規化し、以降は「null＝指定なし」だけを扱えばよい状態にする。
 *
 * <p>要求本文は Jackson が読み {@link WebDataBinder} を通らないため、この正規化の対象外。
 */
@ControllerAdvice
public class RequestParameterTrimmingAdvice {

  @InitBinder
  public void trimStrings(@NonNull WebDataBinder binder) {
    binder.registerCustomEditor(String.class, new BlankToNullEditor());
  }

  /**
   * 前後の空白を除き、空になれば null を与える編集器。
   *
   * <p>{@link String#strip()} を使うのは、日本語入力で頻出する全角スペース（U+3000）を空白として扱う必要があるため。 {@link String#trim()}
   * は U+0020 以下しか除かないので、全角スペースだけの検索語が「絞り込み条件あり」として通り、 結果が常に空になる。
   */
  private static final class BlankToNullEditor extends PropertyEditorSupport {

    @Override
    public void setAsText(@Nullable String text) {
      if (text == null) {
        setValue(null);
        return;
      }
      String stripped = text.strip();
      setValue(stripped.isEmpty() ? null : stripped);
    }

    @Override
    public String getAsText() {
      Object value = getValue();
      return value != null ? value.toString() : "";
    }
  }
}
