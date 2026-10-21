'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { platformCastApi, PlatformCastEnrollmentResponse } from '@/entities/cast';
import { useListPage, useResource } from '@/shared/lib';
import {
  Button,
  Card,
  CardContent,
  RegionError,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';

const statusLabels = { ENROLLED: '在籍中', SUSPENDED: '在籍停止', WITHDRAWN: '退店' };

export function PlatformCastDetailPage() {
  const { id } = useParams<{ id: string }>();
  return <PersonDetail key={id} id={Number(id)} />;
}

function PersonDetail({ id }: { id: number }) {
  const person = useResource(() => platformCastApi.get(id), [id]);
  const back = { href: '/platform/casts', label: '一覧に戻る' };
  if (person.isLoading) return <p role="status">読み込み中...</p>;
  if (person.failure === 'notFound')
    return <RegionError message="キャスト本人が見つかりません" fallback={back} />;
  if (person.failure)
    return (
      <RegionError
        message="本人情報を取得できません。閲覧権限を確認してください。"
        onRetry={person.reload}
      />
    );
  if (!person.data) return null;
  const data = person.data;
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">キャスト本人情報</h1>
        <Button variant="outline" render={<Link href={back.href} />}>
          {back.label}
        </Button>
      </div>
      <Card>
        <CardContent>
          <dl className="grid grid-cols-2 gap-4">
            <div>
              <dt className="text-sm text-muted-foreground">本人ID</dt>
              <dd>{data.id}</dd>
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">アカウントID</dt>
              <dd>{data.platform_user_id}</dd>
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">表示名</dt>
              <dd>{data.display_name}</dd>
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">本名</dt>
              <dd>{data.real_name ?? '未登録'}</dd>
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">生年月日</dt>
              <dd>{data.birth_date ?? '未登録'}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>
      <Enrollments id={id} />
    </div>
  );
}

function Enrollments({ id }: { id: number }) {
  const list = useListPage<PlatformCastEnrollmentResponse>(page =>
    platformCastApi.enrollments(id, { page, size: 20 })
  );
  return (
    <ListPage
      title="店舗在籍"
      description="退店済みの在籍を含み、再入店は別の在籍として表示します。"
      state={list}
      emptyMessage="店舗在籍がありません"
      errorMessage="店舗在籍を取得できません"
      onRetry={list.reload}
    >
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>店舗</TableHead>
            <TableHead>源氏名</TableHead>
            <TableHead>在籍状態</TableHead>
            <TableHead>退店日時</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {list.rows.map(row => (
            <TableRow key={row.id}>
              <TableCell>{row.store_name}</TableCell>
              <TableCell>{row.name}</TableCell>
              <TableCell>{statusLabels[row.status]}</TableCell>
              <TableCell>
                {row.ended_at
                  ? new Date(row.ended_at).toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo' })
                  : '—'}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </ListPage>
  );
}
