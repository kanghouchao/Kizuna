export interface PricingRow {
  item: string;
  /** 行の末尾が金額として読めたときだけ入る。 */
  price?: string;
}

// 空白で区切られた末尾が金額（「10,000円〜」「¥3,000 / 1時間」）で始まる行だけを、品目と金額に分ける
const PRICED = /^(.*?\S)\s+((?:[¥￥]\s?[\d,，.]+|[\d,，.]+\s?円).*)$/u;

// 金額の欄は折り返さずに組む。長い但し書きや、ひらがなの続く文は金額の欄に入れず、行ごと残す
const AMOUNT_MAX_CHARS = 16;
const HIRAGANA = /[\u3041-\u309f]/u;

/**
 * 料金の説明文を表の行にする。文面は一行ずつそのまま残し、消すのは空行だけ。
 * 金額と読めない行は、品目だけの行として同じ順に並べる。
 */
export function pricingRows(text: string): PricingRow[] {
  return text
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map(line => {
      const priced = PRICED.exec(line);
      const amount = priced?.[2] ?? '';
      const splits =
        priced && Array.from(amount).length <= AMOUNT_MAX_CHARS && !HIRAGANA.test(amount);
      return splits ? { item: priced[1], price: amount } : { item: line };
    });
}
