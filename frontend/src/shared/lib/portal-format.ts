// 本人ポータルの書式（DESIGN.md「本人ポータル」の書式表）。どれも引数だけで決まり、現在時刻を読まない。
const WEEKDAYS = ['日', '月', '火', '水', '木', '金', '土'];

const TOKYO = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Tokyo',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** 瞬間を東京の 'yyyy-MM-ddTHH:mm' にする。 */
function toTokyoLocal(instant: Date): string {
  const part = Object.fromEntries(TOKYO.formatToParts(instant).map(p => [p.type, p.value]));
  return `${part.year}-${part.month}-${part.day}T${part.hour}:${part.minute}`;
}

/** 瞬間を東京の暦日 'yyyy-MM-dd' にする。formatDate 等の today に渡す。 */
export function tokyoDate(instant: Date): string {
  return toTokyoLocal(instant).slice(0, 10);
}

/** 'yyyy-MM-dd' を「11/5（水）」に。today と年が違えば「2025/11/5（水）」。 */
export function formatDate(date: string, today: string): string {
  const [y, m, d] = date.slice(0, 10).split('-').map(Number);
  // 暦日だけを扱うので UTC で組む。端末の時差で曜日がずれない。
  const weekday = WEEKDAYS[new Date(Date.UTC(y, m - 1, d)).getUTCDay()];
  const prefix = String(y) === today.slice(0, 4) ? '' : `${y}/`;
  return `${prefix}${m}/${d}（${weekday}）`;
}

/** 'HH:mm[:ss]' を「9:30」に（時は 0 埋めしない）。 */
function formatClock(time: string): string {
  const [h, m] = time.split(':');
  return `${Number(h)}:${m}`;
}

/** 'HH:mm[:ss]' の時間帯を「19:00〜21:00」に。終了が開始以前なら日をまたぐとみなし「19:00〜翌2:00」。 */
export function formatTimeRange(start: string, end: string): string {
  const crossesMidnight = end.slice(0, 5) <= start.slice(0, 5);
  return `${formatClock(start)}〜${crossesMidnight ? '翌' : ''}${formatClock(end)}`;
}

/**
 * 日時を「11/5（水）19:00」に。時差付き（Z・+09:00）の瞬間は東京の時刻に直し、
 * 時差の無い 'yyyy-MM-ddTHH:mm[:ss]' は営業上の日時としてそのまま読む。
 */
export function formatDateTime(value: string, today: string): string {
  const hasZone = /(Z|[+-]\d{2}:?\d{2})$/.test(value);
  const local = hasZone ? toTokyoLocal(new Date(value)) : value;
  return `${formatDate(local, today)}${formatClock(local.slice(11, 16))}`;
}

/** サービスの長さ（コース等）。「90分」。 */
export function formatMinutes(minutes: number): string {
  return `${minutes}分`;
}

/** 出勤などの経過時間。「1時間30分」、0 の単位は省く。 */
export function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m}分`;
  return m === 0 ? `${h}時間` : `${h}時間${m}分`;
}

/** 金額「¥12,000」。負はマイナス記号（U+2212）を前に置き「−¥1,000」。 */
export function formatMoney(amount: number): string {
  const body = `¥${Math.abs(amount).toLocaleString('ja-JP')}`;
  return amount < 0 ? `−${body}` : body;
}

/** 金額の変更「¥8,000 → ¥10,000」。 */
export function formatMoneyChange(before: number, after: number): string {
  return `${formatMoney(before)} → ${formatMoney(after)}`;
}

/** 版「第2版」。 */
export function formatRevision(revision: number): string {
  return `第${revision}版`;
}
