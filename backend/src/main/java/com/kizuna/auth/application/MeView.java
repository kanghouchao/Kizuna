package com.kizuna.auth.application;

import java.util.List;

/** 本人資料。permissions は保持ロールの権限並集（enum 名の昇順）、lineLinked は LINE 連携の有無だけを持つ。 */
public record MeView(
    String email,
    String displayName,
    String userType,
    List<String> permissions,
    boolean lineLinked) {}
