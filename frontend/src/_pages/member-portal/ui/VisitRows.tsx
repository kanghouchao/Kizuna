import type { MemberVisit } from '@/entities/order';
import { formatDate, formatPointChange } from '@/shared/lib';
import { ListRow, ListRows } from '@/shared/ui';

/** 来店の一覧（日付・店舗・獲得ポイント）。ポイントの画面の最近の来店と来店履歴のすべてが同じ行を使う。 */
export function VisitRows({ visits, today }: { visits: MemberVisit[]; today: string }) {
  return (
    <ListRows label="来店履歴">
      {/* 来店は台帳内部の ID を持たない（応答に出さない）。1 ページの中で並べ替えも削除も
          起きないため、ページ内の位置がそのまま識別子になる。 */}
      {visits.map((visit, index) => (
        <ListRow
          key={index}
          title={visit.visited_on ? formatDate(visit.visited_on, today) : '日付不明'}
          meta={visit.store_name}
          trailing={
            <span
              className={`tabular-nums ${visit.granted_points > 0 ? 'font-semibold text-primary-strong' : 'text-muted-foreground'}`}
            >
              {formatPointChange(visit.granted_points)}
            </span>
          }
        />
      ))}
    </ListRows>
  );
}

/** ポイントと来店は、店舗が会計時に出す伝票の QR を読み取ると取り込まれる。申領の画面への入口は QR だけ。 */
export function ReceiptHint() {
  return (
    <p className="text-muted-foreground">
      来店時に店舗から受け取る伝票の QR を読み取ると、来店とポイントがここに並びます。
    </p>
  );
}
