/**
 * 受注のフォーム値と契約の値の往復。
 *
 * 空欄を `undefined`（＝キーごと送らない）へ翻すのは共通の機構で、その `undefined` が何を意味するかは
 * 契約ごとに違う — 部分更新の口では「変更しない」、全量送信の口では「値なし」になる。呼出側がその一言を持つ。
 */

/**
 * 時刻は秒まで返るが、入力欄（type=time）は分までしか扱わない。
 *
 * 秒は落ちて {@link optionalTime} が `:00` を当て直す。この画面群が書く時刻は常に `:00` なので
 * 今は往復で値が変わらないが、秒を持つ書き手が現れたら分までに丸められる。
 */
export function toTimeInput(value: string | undefined): string {
  return value ? value.slice(0, 5) : '';
}

/** 入力欄の `HH:mm` を契約の `HH:mm:ss` へ。空欄は送らない。 */
export function optionalTime(value: string): string | undefined {
  return value === '' ? undefined : `${value}:00`;
}

export function optionalNumber(value: string): number | undefined {
  return value.trim() === '' ? undefined : Number(value);
}

export function optionalDate(value: string): string | undefined {
  return value === '' ? undefined : value;
}
