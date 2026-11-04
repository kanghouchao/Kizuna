package com.kizuna.settings.application;

/** ポイント期限前案内の設定。{@code days} は失効の何日前に案内するかで、未設定・範囲外は null（処理は実行を拒む）。 */
public record PointExpiryNoticeSettings(Integer days) {}
