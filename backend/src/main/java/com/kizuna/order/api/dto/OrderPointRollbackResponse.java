package com.kizuna.order.api.dto;

/**
 * 巻き戻しで実際に動いた量。仕訳ゼロの受注では両方 0 になるが、操作記録は書かれている。
 *
 * @param cancelledPoints 取消で無効化した付与の未消費残の合計
 * @param restoredPoints 利用の逆転で元のロットへ返した合計
 */
public record OrderPointRollbackResponse(int cancelledPoints, int restoredPoints) {}
