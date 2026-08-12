package com.kizuna.order.api.dto;

import java.time.LocalDate;

/**
 * 会員本人に返す来店 1 件の表現（JSON キーは snake_case）。
 *
 * <p>出すのは来店日・店舗名・人数・担当キャスト名と、その来店で得たポイントだけ。会計金額・利用ポイントは店舗の会計の内部事情、
 * ランク・区分・NG・連絡先などは店舗の顧客台帳の内部情報であり、本人であっても会員側の経路からは到達できてはならない。
 *
 * @param visitedOn 来店日（受注の業務日）
 * @param storeName 来店した店舗の表示名
 * @param pax 人数。受注に記録が無ければ欠落する
 * @param castName 担当キャストの表示名。指名も割り当ても無い来店では欠落する
 * @param grantedPoints その来店で得たポイント。台帳に付与行が無ければ 0（0 円完了・付与設定なし）
 */
public record MemberVisitResponse(
    LocalDate visitedOn, String storeName, Integer pax, String castName, int grantedPoints) {}
