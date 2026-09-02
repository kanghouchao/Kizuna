/**
 * storeprofile モジュールのドメイン層。
 *
 * <p>named interface 公開は過渡措置: store モジュールの店舗登録がデフォルト StoreProfile の生成・保存を直接行っているため。
 * イベント駆動化は挙動変化を伴うため見送っており、イベント化の際に公開面を狭める。
 */
@org.springframework.modulith.NamedInterface("domain")
package com.kizuna.storeprofile.domain;
