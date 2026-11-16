const hourMinute = (time: string | undefined) => (time ?? '').slice(0, 5);

/** 出勤の時間帯。00:00 の終了は日をまたいだ続きとして 24:00 と書く。 */
export function shiftHours(start: string | undefined, end: string | undefined): string {
  const until = hourMinute(end);
  return `${hourMinute(start)}–${until === '00:00' ? '24:00' : until}`;
}

/** "YYYY-MM-DD" → "YYYY年M月D日"。店舗の現地日付をそのまま読むため Date を経由しない。 */
export function localDate(value: string): string {
  const [year, month, day] = value.split('-').map(Number);
  return `${year}年${month}月${day}日`;
}

const storeDate = new Intl.DateTimeFormat('ja-JP', {
  timeZone: 'Asia/Tokyo',
  year: 'numeric',
  month: 'long',
  day: 'numeric',
});

/** 記事の公開開始の日時を、店舗の現地日付にする。 */
export function publishedDate(value: string): string {
  return storeDate.format(new Date(value));
}
