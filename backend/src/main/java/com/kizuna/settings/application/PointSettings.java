package com.kizuna.settings.application;

/**
 * ポイント制度の型付きスナップショット。キー名（point_grant_unit_amount 等）の知識は settings モジュールだけが持ち、 消費側（point）はこの型のみに依存する。
 *
 * @param grantUnitAmount 付与の単位金額（円）。0 以下は付与しない設定として扱われる
 * @param grantPointsPerUnit 単位あたりの付与ポイント。0 以下は付与しない設定として扱われる
 * @param usageUnit 利用ポイントの単位。0 以下は 1 として扱われる
 */
public record PointSettings(int grantUnitAmount, int grantPointsPerUnit, int usageUnit) {}
