package com.kizuna.shared.web;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * クエリ・パス変数の文字列を束縛の時点で trim し、空になったものは null にする。
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
    binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
  }
}
