/**
 * cast モジュールのアプリケーション層。
 *
 * <p>named interface 公開は過渡措置: shift モジュールが出勤登録時に「当該キャストが現在テナントに属するか」を CastService 経由で確認するため。読み側 API
 * の整備後に公開面を狭める。
 */
@org.springframework.modulith.NamedInterface("application")
package com.kizuna.cast.application;
