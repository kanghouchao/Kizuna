package com.kizuna.member.application;

/** 会員検索の状態条件。既定は有効（未退会）で、退会済みは明示して切り替える。 */
public enum MemberStatusFilter {
  ACTIVE,
  WITHDRAWN,
  ALL
}
