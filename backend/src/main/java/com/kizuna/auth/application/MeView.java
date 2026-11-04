package com.kizuna.auth.application;

import java.util.List;

/** GET /me で返す本人資料。 */
public record MeView(
    String email,
    String displayName,
    String userType,
    List<String> permissions,
    boolean lineLinked,
    boolean emailVerified) {}
