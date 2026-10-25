package com.kizuna.user.api.dto;

/** 初期設定の要否。本人種別 STAFF の行が 1 件も無いときだけ required になる。 */
public record InitialSetupStatusResponse(boolean required) {}
