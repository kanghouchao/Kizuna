/**
 * settings モジュールのアプリケーション層。
 *
 * <p>公開面のうち BusinessDateService は恒久的な公開型（営業日の判定は shift・order が共有する）。SystemConfigService
 * の公開は過渡措置で、store のメンテナンスモード実施・notification の SMTP 動的設定・auth の LINE 資格情報・point の付与設定・member のランク閾値が
 * 直接参照している。読み側 API の整備後にこちらだけ公開面を狭める。
 */
@org.springframework.modulith.NamedInterface("application")
package com.kizuna.settings.application;
