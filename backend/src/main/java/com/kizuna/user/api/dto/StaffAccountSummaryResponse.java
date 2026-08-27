package com.kizuna.user.api.dto;

import java.util.List;

/**
 * アカウント面の一覧要約。JSON キーは Jackson 設定により snake_case（display_name）。
 *
 * <p>version を持たないのは、この面が停止・再開しか行わず、どちらも版の往復を要らない冪等な動作だからである。 授権（ロール・店舗集合）を書ける項目は型として存在しない。
 */
public record StaffAccountSummaryResponse(
    Long id, String email, String displayName, boolean enabled, List<StaffAccountRoleRef> roles) {}
