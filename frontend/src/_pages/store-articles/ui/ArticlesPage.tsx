'use client';
import Link from 'next/link';
import { PlusIcon } from 'lucide-react';
import { articleApi } from '@/entities/content/article';
import { publicationSummary } from '@/features/publication-form';
import { storePath, useListPage } from '@/shared/lib';
import { Button, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import { ArticleAccess } from './ArticleAccess';
import { DisplayedBadge } from './DisplayedBadge';

export function ArticlesPage() {
  return (
    <ArticleAccess>
      <Articles />
    </ArticleAccess>
  );
}

function Articles() {
  const list = useListPage((page: number) => articleApi.list(page));
  return (
    <ListPage
      title="記事管理"
      description="公式サイトに載せる記事を作成し、公開設定を管理します。"
      actions={
        <Button render={<Link href={storePath('/articles/create')} />}>
          <PlusIcon />
          記事を作成
        </Button>
      }
      state={list}
      emptyMessage="記事はありません"
      errorMessage="記事一覧を取得できませんでした"
      onRetry={() => void list.reload()}
    >
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>表題</TableHead>
            <TableHead>掲出</TableHead>
            <TableHead>公開設定</TableHead>
            <TableHead>更新日時</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {list.rows.map(row => (
            <TableRow key={row.id}>
              <TableCell className="max-w-80">
                <Link
                  href={storePath(`/articles/${row.id}/edit`)}
                  className="font-medium text-primary-strong hover:underline wrap-anywhere focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  {row.title}
                </Link>
              </TableCell>
              <TableCell>
                <DisplayedBadge displayed={row.displayed} />
              </TableCell>
              <TableCell className="text-muted-foreground">
                {publicationSummary(row.publication)}
              </TableCell>
              <TableCell className="text-muted-foreground">
                {new Date(row.updated_at).toLocaleString('ja-JP')}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </ListPage>
  );
}
