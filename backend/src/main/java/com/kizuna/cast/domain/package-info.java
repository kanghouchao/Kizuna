/**
 * cast モジュールのドメイン層。
 *
 * <p>named interface 公開は過渡措置: order モジュールの注文作成がキャスト存在確認のため リポジトリを直接参照している。shift
 * モジュールも公開出勤表のキャスト表示情報取得のため {@code CastRepository} を直接参照している。読み側 API の整備後に公開面を狭める。
 */
@org.springframework.modulith.NamedInterface("domain")
package com.kizuna.cast.domain;
