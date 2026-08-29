package com.kizuna.order.api.dto;

/**
 * 巻き戻しの下見。実行前に「何がいくら動くか」を示すためだけの読み口で、台帳の行は載せない。
 *
 * <p>会員の残高も載せない。この受注の授受に限って示すのであって、店舗へ会員の資産を渡す口ではない（ADR 0006）。
 *
 * @param alreadyRolledBack 既に巻き戻し済みか。真なら二度目の要求は 409 になる
 * @param memberCode この受注が現に帰属している会員のコード。帰属していなければ null
 * @param cancellablePoints 取消で無効化される付与の未消費残の合計
 * @param reversibleUsedPoints 逆転で元のロットへ返る利用の合計
 */
public record OrderPointRollbackPreviewResponse(
    boolean alreadyRolledBack,
    String memberCode,
    int cancellablePoints,
    int reversibleUsedPoints) {}
