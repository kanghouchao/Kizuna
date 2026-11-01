const storeDate = new Intl.DateTimeFormat('ja-JP', {
  timeZone: 'Asia/Tokyo',
  year: 'numeric',
  month: 'long',
  day: 'numeric',
});

/** 記事の公開開始の日時を、店舗の現地日付で表示する。 */
export default function PublishedOn({ value }: { value: string }) {
  return <time dateTime={value}>{storeDate.format(new Date(value))}</time>;
}
