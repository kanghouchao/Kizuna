package com.kizuna.user.api.dto;

/**
 * 店長設定の一覧 1 件。JSON キーは Jackson 設定により snake_case。
 *
 * <p>ロール・担当店舗集合は返さない — この面が扱うのは「この店舗の店長か否か」だけで、授権の中身の編集は 店舗スタッフ管理・管理者管理の領分である（ADR 0020）。
 */
public record StoreManagerResponse(Long id, String email, String displayName, boolean enabled) {}
