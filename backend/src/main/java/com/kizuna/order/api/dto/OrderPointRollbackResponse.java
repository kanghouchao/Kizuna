package com.kizuna.order.api.dto;

/**
 * 巻き戻しで実際に動いた量。仕訳ゼロの受注では両方 0 になるが、操作記録は書かれている。
 *
 * <p>合計は long。1 件の仕訳は int でも、1 受注に複数の付与が積まれた合計は int を超えうる。
 *
 * @param cancelledPoints 取消で無効化した付与の未消費残の合計
 * @param restoredPoints 利用の逆転で元のロットへ返した合計
 */
public record OrderPointRollbackResponse(long cancelledPoints, long restoredPoints) {}
