/**
 * point モジュールのアプリケーション層。
 *
 * <p>公開するのは受注の完了処理（付与・利用・その事前計算）と、顧客側からの残高照会・手動調整だけ。台帳の仕訳そのものは 公開しない —
 * 残高は行の合計としてのみ意味を持ち、外から個々の行を組み立てられると追加型台帳の不変条件が破れるため。
 */
@org.springframework.modulith.NamedInterface("application")
package com.kizuna.point.application;
