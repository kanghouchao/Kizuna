package com.kizuna.shared.exception;

/**
 * 指名された業務資源が見つからないことを表す例外基底。HTTP 404 で応答される。
 *
 * <p>「見つからない」に 404 を割り当てる境界は、<b>呼出側が何を指名したか</b>で決まる。
 *
 * <ul>
 *   <li><b>行の id を指名した</b>場合は本例外（404）。その行が他店舗に存在するか否かは呼出側が知ってよい情報ではなく、404
 *       は「本当に存在しない」と区別がつかないため漏洩しない。加えて {@code storeFilter} 有効時のサービスは他店舗の行の存在を そもそも判定できず、403
 *       を返すには作用域を外した問い合わせを別途行う必要がある — それは濾過機構に穴を開けることになるため採らない。
 *   <li><b>店舗を指名した</b>（{@code X-Store-ID}）場合は 403。店舗は公開ドメインを持ち存在自体が秘匿対象ではないため、 授権の有無をそのまま答えてよい。
 * </ul>
 *
 * <p>授権判定の一部として実在性を確かめる経路（{@link com.kizuna.shared.storescope.StoreScopeExecutor}）は本例外を使わない。 そこで
 * 404 を返すと、403（授権なし）との対比で「どの店舗 id が実在するか」が判別可能になるため。
 */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}
