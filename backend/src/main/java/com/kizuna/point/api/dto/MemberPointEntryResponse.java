package com.kizuna.point.api.dto;

import java.time.LocalDate;

/**
 * 会員本人に返すポイント明細 1 行（JSON キーは snake_case）。
 *
 * <p>出すのは日付・発生店舗名・種別・増減と、加算に期限があればその期限だけ。理由（reason）は店員が書く運用内部の文言、 会計金額・引き当ては台帳内部の事情であり、いずれも本人向けの
 * 表示には要らないので持たない。
 *
 * @param occurredOn 記帳日（業務のタイムゾーンで畳んだ日付）
 * @param storeName 発生店舗の表示名。失効のような系統イベントと削除済み店舗では欠落し、non_null 包含により項目ごと落ちる
 * @param entryType 仕訳種別（{@code PointEntryType} の名前）
 * @param amount 符号付きの増減
 * @param expiresOn 加算ロットの有効期限。期限なしと減算では欠落する
 */
public record MemberPointEntryResponse(
    LocalDate occurredOn, String storeName, String entryType, int amount, LocalDate expiresOn) {}
