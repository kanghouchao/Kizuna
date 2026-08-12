/**
 * point モジュールのアプリケーション層。
 *
 * <p>公開するのは受注の完了処理（付与・利用・その事前計算）と、顧客側からの残高照会・手動調整、および店舗に帰属する 仕訳の有無だけ。台帳の集約（{@code
 * PointEntry}）そのものは公開しない — 残高は行の合計としてのみ意味を持ち、外から個々の行を組み立てられると追加型台帳の不変条件が破れるため。
 *
 * <p>会員本人向けの明細の読み口（{@code MemberPointService}）はこのモジュール自身が {@code /platform/me} 配下へ露出する。返すのは表示用の 読み側
 * projection で、引き当て・元取引・理由・実行者を持たず、書き込みの口も伴わない。
 */
@org.springframework.modulith.NamedInterface("application")
package com.kizuna.point.application;
