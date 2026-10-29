package com.kizuna.settings.application;

/**
 * 応募者の終端状態ごとの保持期間（月数）の型付きスナップショット。キー名の知識は settings モジュールだけが持つ。
 *
 * <p>null は未設定で「自動匿名化しない」を意味する。未設定を 0 か月と読むと、方針を決める前に終端の応募者が匿名化される。
 */
public record ApplicantRetentionSettings(
    Integer rejectedMonths, Integer withdrawnMonths, Integer hiredMonths) {}
