/**
 * settings モジュールのアプリケーション層。
 *
 * <p>公開面のうち BusinessDateService は恒久的な公開型（営業日の判定は shift・order が共有する）。
 *
 * <p>SystemConfigService の公開は過渡措置で、notification・auth・point・member・recruitment が直接参照している。
 *
 * <p>参照先はそれぞれ SMTP・LINE 資格情報・付与設定・ランク閾値・応募者の保持期間。読み側 API の整備後にこちらだけ公開面を狭める。
 */
@org.springframework.modulith.NamedInterface("application")
package com.kizuna.settings.application;
