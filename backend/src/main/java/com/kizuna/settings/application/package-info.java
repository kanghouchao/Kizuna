/**
 * settings モジュールのアプリケーション層。
 *
 * <p>named interface 公開は過渡措置: store のメンテナンスモード実施と notification の SMTP 動的設定が SystemConfigService
 * を直接参照している。読み側 API の整備後に公開面を狭める。
 */
@org.springframework.modulith.NamedInterface("application")
package com.kizuna.settings.application;
