/**
 * storeprofile モジュールのドメイン層。
 *
 * <p>named interface 公開は過渡措置: store モジュールの開通処理がデフォルト StoreProfile の生成・保存を 直接行っている（D4
 * のイベント駆動化は挙動変化を伴うため見送り）。イベント化の際に公開面を狭める。
 */
@org.springframework.modulith.NamedInterface("domain")
package com.kizuna.storeprofile.domain;
