import { OrderFeeLine, OrderFeeLineInput, OrderFeeLineKind } from './types';

/**
 * 店舗が差し替えられる明細だけを入力の形で取り出す。
 *
 * 送り返す内訳にシステム専有の行（ポイント利用）を混ぜるとサーバが撥ねるため、編集画面の初期値はここを通す。
 */
export function storeEditableFeeLines(lines: OrderFeeLine[] | undefined): OrderFeeLineInput[] {
  return (lines ?? [])
    .filter(line => !line.system_owned)
    .map(line => ({ kind: line.kind, name: line.name ?? '', amount: line.amount }));
}

/** 完了処理が書いた明細（ポイント利用）。編集できないので、読み取り専用で並べるためだけに使う。 */
export function systemOwnedFeeLines(lines: OrderFeeLine[] | undefined): OrderFeeLine[] {
  return (lines ?? []).filter(line => line.system_owned);
}

/** 符号が減算に固定された種別。入力も表示も正値なので、足すときだけ符号を戻す。 */
function isDeduction(kind: OrderFeeLineKind): boolean {
  return kind === 'DISCOUNT' || kind === 'POINT_REDEMPTION';
}

/** 明細の総和（表示上の値から符号を戻して足す）。合計の正本はサーバ側の導出で、これは入力中の目安。 */
export function feeLinesTotal(lines: OrderFeeLineInput[]): number {
  return lines.reduce((sum, line) => {
    const amount = Number.isNaN(line.amount) ? 0 : line.amount;
    return sum + (isDeduction(line.kind) ? -amount : amount);
  }, 0);
}
