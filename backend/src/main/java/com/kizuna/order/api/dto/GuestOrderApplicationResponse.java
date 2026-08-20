package com.kizuna.order.api.dto;

/**
 * ゲスト予約申請の受付応答。匿名の申請者には後から申請を読む口が無いため、受理された事実と受付番号だけを返す。
 *
 * <p>申請の内容は返さない — 送った本人だけが読めることを保証する手立てがこの経路には無い。
 */
public record GuestOrderApplicationResponse(String id) {}
