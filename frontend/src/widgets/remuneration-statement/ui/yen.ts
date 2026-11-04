export const yen = (value: number | undefined) =>
  value === undefined ? '計算待ち' : `¥${value.toLocaleString('ja-JP')}`;
