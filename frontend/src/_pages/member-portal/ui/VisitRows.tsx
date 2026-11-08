import type { MemberVisit } from '@/entities/order';
import { formatDate } from '@/shared/lib';
import { ListRow, ListRows } from '@/shared/ui';
import { formatPointChange } from '../lib/points';

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
          // 付与の無い来店（0 円完了）に「+0」を出すと、取り込めていない来店に見える
          trailing={
            visit.granted_points > 0 && (
              <span className="font-semibold text-primary-strong tabular-nums">
                {formatPointChange(visit.granted_points)}
              </span>
            )
          }
        />
      ))}
    </ListRows>
  );
}
