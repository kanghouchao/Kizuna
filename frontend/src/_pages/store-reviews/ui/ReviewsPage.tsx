'use client';
import Link from 'next/link';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { MePending, hasPermission, useMe, useStoreContext } from '@/entities/user';
import { ReviewSearch, reviewApi } from '@/entities/content/review';
import { storePath, useListPage } from '@/shared/lib';
import { Button, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import { useReviewWrite } from '../lib/useReviewWrite';
import { statuses, permissions, time } from './labels';
import { ReviewFilters } from './ReviewFilters';
import { ReviewWriteFeedback } from './ReviewWriteFeedback';
import { accessError } from './reviewErrors';
export function ReviewsPage() {
  const { me } = useMe();
  const storeId = useStoreContext().currentStoreId;
  if (me === null) return <MePending />;
  if (me.user_type !== 'STAFF' || !hasPermission(me, 'REVIEW_VIEW'))
    return <div role="alert">口コミの閲覧権限がありません</div>;
  return <Reviews key={storeId} canManage={hasPermission(me, 'REVIEW_MANAGE')} />;
}
function Reviews({ canManage }: { canManage: boolean }) {
  const router = useRouter();
  const [filters, setFilters] = useState<ReviewSearch>({});
  const [filterError, setFilterError] = useState<string | null>(null);
  const list = useListPage((page, criteria: ReviewSearch) => reviewApi.list(page, criteria), {});
  const search = () => {
    if (
      (filters.q?.trim().length ?? 0) > 60 ||
      (filters.review_id && !/^[0-9]{1,32}$/.test(filters.review_id))
    ) {
      setFilterError('表示名は60文字以内、口コミIDは1〜32桁の数字で指定してください');
      return;
    }
    setFilterError(null);
    void list.search({
      ...filters,
      q: filters.q?.trim() || undefined,
      review_id: filters.review_id || undefined,
    });
  };
  const write = useReviewWrite(review => router.push(storePath(`/reviews/${review.id}`)));
  return (
    <>
      <ReviewWriteFeedback write={write} />
      <ListPage
        title="口コミ管理"
        description="スタッフが受け付けた口コミを審査し、公開許可を別途記録します。"
        actions={
          canManage && (
            <Button render={<Link href={storePath('/reviews/create')} />}>口コミを受付</Button>
          )
        }
        search={{
          content: <ReviewFilters value={filters} onChange={setFilters} error={filterError} />,
          onSearch: search,
        }}
        state={{ ...list }}
        emptyMessage="口コミはありません"
        errorMessage={
          accessError(list.error)?.replace('操作', '閲覧') ?? '口コミ一覧を取得できませんでした'
        }
        onRetry={() => void list.reload()}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>受付・表示名</TableHead>
              <TableHead>審査</TableHead>
              <TableHead>公開許可</TableHead>
              <TableHead>掲出</TableHead>
              <TableHead>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {list.rows.map(r => (
              <TableRow key={r.id}>
                <TableCell>
                  <p className="wrap-anywhere">{r.display_name ?? '匿名'}</p>
                  <p>{time(r.received_at)}</p>
                  <p className="text-sm text-muted-foreground">スタッフによる記録</p>
                </TableCell>
                <TableCell>{statuses[r.status]}</TableCell>
                <TableCell>{permissions[r.permission_status]}</TableCell>
                <TableCell>{r.displayed ? '掲出対象' : '—'}</TableCell>
                <TableCell>
                  <Button variant="outline" render={<Link href={storePath(`/reviews/${r.id}`)} />}>
                    内容・履歴
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
    </>
  );
}
