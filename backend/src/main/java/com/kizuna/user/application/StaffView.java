package com.kizuna.user.application;

import java.util.List;
import java.util.Set;

/** 従業員の現在の授権。editable は委譲守衛の判定結果で、行使者ごとに変わる。 */
public record StaffView(
    Long id,
    String email,
    String displayName,
    boolean enabled,
    List<RoleViews.Ref> roles,
    Set<Long> storeIds,
    long version,
    boolean editable) {}
