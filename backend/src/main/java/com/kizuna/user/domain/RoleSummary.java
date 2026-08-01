package com.kizuna.user.domain;

/** ロール一覧の読み側 projection。権限は個数だけを集計し、権限集合そのものは取得しない。 */
public interface RoleSummary {

  Long getId();

  String getName();

  Boolean getSystemRole();

  long getPermissionCount();
}
