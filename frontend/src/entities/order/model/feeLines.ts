import { OrderFeeLine, OrderFeeLineInput } from './types';

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
