package com.kizuna.order.domain;

/**
 * 受注明細の種別。閉じた八枚で、ホテル代・交通費・釣銭のような受注金額外の回収・精算項目は型として持てない。
 *
 * <p>加算の細分（指名・受付区分・場所エリア等）は {@link #SURCHARGE} に集約し、区別は行の名称の写しが担う。
 *
 * <p>保存する金額は帯符号で、合計は行の単純総和になる。減項を正値へ翻すのは表示の作法で、往復の算術はこの enum が単一の出所として持つ。
 */
public enum OrderFeeLineKind {

  /** 基本コース料金。行の名称は受注のコース名の写しから採る。 */
  BASE_COURSE(Sign.ADDITION),

  /** 延長料金。延長分数の写しは受注が持ち、この行は金額だけを担う。 */
  EXTENSION(Sign.ADDITION),

  /** オプション料金。1 件 1 行で、名称は手入力。 */
  OPTION(Sign.ADDITION),

  /** 指名・受付区分・場所エリア等の加算の総称。 */
  SURCHARGE(Sign.ADDITION),

  /** 割引。 */
  DISCOUNT(Sign.DEDUCTION),

  /** 手動調整。合計を機械和から外したい場面をこの行で表すため、符号を縛らない。 */
  MANUAL_ADJUST(Sign.EITHER),

  /** ポイント利用。完了処理が台帳の減算仕訳と対で書く唯一の行で、店舗の通常編集からは作れない。 */
  POINT_REDEMPTION(Sign.DEDUCTION),

  /** クレジット利用時加算。 */
  CREDIT_SURCHARGE(Sign.ADDITION);

  private enum Sign {
    ADDITION,
    DEDUCTION,
    EITHER
  }

  private final Sign sign;

  OrderFeeLineKind(Sign sign) {
    this.sign = sign;
  }

  /** 保存する帯符号金額がこの種別の符号約定に合うか。DB 側の CHECK 制約と同じ判定を集約の側で先に行う。 */
  public boolean allows(int amount) {
    return switch (sign) {
      case ADDITION -> amount >= 0;
      case DEDUCTION -> amount <= 0;
      case EITHER -> true;
    };
  }

  /** 符号が減算に固定された種別か。表示層はこの種別だけ正値へ翻す。 */
  public boolean isDeduction() {
    return sign == Sign.DEDUCTION;
  }

  /** 完了処理だけが書く行か。店舗が差し替える明細にこの種別は含められない。 */
  public boolean isSystemOwned() {
    return this == POINT_REDEMPTION;
  }

  /** 表示上の金額を保存する帯符号金額へ翻す。 */
  public int signedAmountOf(int displayedAmount) {
    return isDeduction() ? -displayedAmount : displayedAmount;
  }

  /** 保存された帯符号金額を表示上の金額へ翻す。 */
  public int displayedAmountOf(int signedAmount) {
    return isDeduction() ? -signedAmount : signedAmount;
  }
}
