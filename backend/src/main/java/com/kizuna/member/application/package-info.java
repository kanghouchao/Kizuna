/**
 * member モジュールのアプリケーション層。
 *
 * <p>公開の意図は 2 つに分かれる。{@code MemberLookupService} は店舗側が会員コードの実在だけを確かめるための恒久的な読み口で、 会員 ID
 * とコード以外を返さないことで公開面そのものを狭く保つ。一方 {@code MemberRegistrationService} の公開は過渡措置: auth モジュールの LINE
 * 登録が会員身分の作成（プラットフォームユーザー + 会員集約の単一トランザクション）を委ねているためで、 登録経路の受け口が整理された段階で狭める。
 */
@org.springframework.modulith.NamedInterface("application")
package com.kizuna.member.application;
