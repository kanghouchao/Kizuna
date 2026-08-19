/** キャスト選択の Select が共有する境界。シフトの追加・編集と実績の記録が同じ形で選ぶ。 */

/**
 * 「候補なし」の案内項目に与える番兵値。Base UI の「値なし」は null であって '' ではないため、
 * フォームが持つ空文字とは別の値が要る。
 */
export const SELECT_NONE = '__none__';

/**
 * 引き金に出す値。候補に無い値（在籍を外れたキャストなど）は未選択として null に倒す。
 * 引き金の文言は候補一覧から引かれるので、素通しすると生の ID がそのまま出る。
 */
export function castValue(value: string, options: { value: string }[]): string | null {
  const candidate = value || SELECT_NONE;
  return options.some(o => o.value === candidate) ? candidate : null;
}
