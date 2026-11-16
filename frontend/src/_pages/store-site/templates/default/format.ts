const storeDate = new Intl.DateTimeFormat('ja-JP', {
  timeZone: 'Asia/Tokyo',
  year: 'numeric',
  month: 'long',
  day: 'numeric',
});

/** 公開開始の日時を、店舗の現地日付にする。 */
export const publishedOn = (value: string) => storeDate.format(new Date(value));

/** "YYYY-MM-DD" を "YYYY年M月D日" にする。店舗の現地日付をそのまま読むので Date を経由しない。 */
export function localDate(value: string): string {
  const [y, m, d] = value.split('-').map(Number);
  return `${y}年${m}月${d}日`;
}

const hhmm = (time: string | undefined) => (time ?? '').slice(0, 5);

/** 出勤の時間帯。00:00 の終了は日をまたいだ続きとして 24:00 と書く。 */
export function shiftHours(start: string | undefined, end: string | undefined): string {
  const until = hhmm(end);
  return `${hhmm(start)}–${until === '00:00' ? '24:00' : until}`;
}

export type PriceBlock =
  { kind: 'table'; rows: { item: string; price: string }[] } | { kind: 'note'; lines: string[] };

const hasAmount = (word: string) => /[0-9０-９]/.test(word) && /[円¥￥]/.test(word);

/**
 * 料金の説明文を、表と但し書きの並びに分ける。行の最後の語が金額（数字と 円 / ¥ を含む）なら
 * その手前を項目名とする表の行、それ以外は但し書きの行で、空行は区切りになる。文字は捨てず、
 * 順序も変えない。
 */
export function priceBlocks(text: string): PriceBlock[] {
  const blocks: PriceBlock[] = [];
  let open: PriceBlock | null = null;
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (!line) {
      open = null;
      continue;
    }
    const cut = Math.max(line.lastIndexOf(' '), line.lastIndexOf('　'), line.lastIndexOf('\t'));
    const price = cut > 0 ? line.slice(cut + 1) : '';
    if (hasAmount(price)) {
      if (open?.kind !== 'table') blocks.push((open = { kind: 'table', rows: [] }));
      open.rows.push({ item: line.slice(0, cut).trim(), price });
    } else {
      if (open?.kind !== 'note') blocks.push((open = { kind: 'note', lines: [] }));
      open.lines.push(line);
    }
  }
  return blocks;
}
