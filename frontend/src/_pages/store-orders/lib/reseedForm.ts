import type { FieldValues, UseFormReturn } from 'react-hook-form';

// フォームごとの、最後に播いた明細。操作者が明細を触ったかはこれとの突き合わせで決める
const seededFeeLines = new WeakMap<object, string>();

/**
 * 取得した内容でフォームを播く。keepInput のときは操作者が触った欄を残す。
 * 明細は触っていれば配列ごと残す。keepDirtyValues は行の削除を残さず、消した行が戻って請求の内訳が変わる。
 */
export function reseedForm<T extends FieldValues & { fee_lines: unknown[] }>(
  form: UseFormReturn<T>,
  latest: T,
  keepInput: boolean
): void {
  const lines = form.getValues().fee_lines;
  const edited = keepInput && JSON.stringify(lines) !== seededFeeLines.get(form.control);
  seededFeeLines.set(form.control, JSON.stringify(latest.fee_lines));
  form.reset(edited ? { ...latest, fee_lines: lines } : latest, { keepDirtyValues: keepInput });
}
