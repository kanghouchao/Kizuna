/**
 * member モジュールのアプリケーション層。
 *
 * <p>named interface 公開は過渡措置: auth モジュールの LINE 登録が会員身分の作成（プラットフォームユーザー + 会員集約の 単一トランザクション）を
 * MemberRegistrationService に委ねているため。登録経路の受け口が整理された段階で公開面を狭める。
 */
@org.springframework.modulith.NamedInterface("application")
package com.kizuna.member.application;
