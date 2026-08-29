package com.kizuna.member.domain;

/** 会員の最小表現の読み側 projection。実体を読み込まずに ID とコードだけを渡す。 */
public interface MemberIdentityView {

  Long getId();

  String getMemberCode();
}
