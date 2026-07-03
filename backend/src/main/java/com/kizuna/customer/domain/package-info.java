/**
 * customer モジュールのドメイン層。
 *
 * <p>named interface 公開は過渡措置: order モジュールの顧客スマートリンク（電話番号による 検索または新規作成）が顧客集約とリポジトリを直接参照している。読み側 API の整備後に公開面を狭める。
 */
@org.springframework.modulith.NamedInterface("domain")
package com.kizuna.customer.domain;
