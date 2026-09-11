'use client';

import { useState } from 'react';
import Link from 'next/link';
import { platformCastApi, PlatformCastSummaryResponse } from '@/entities/cast';
import { useListPage } from '@/shared/lib';
import {
  Button,
  Input,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';

export function PlatformCastsPage() {
  const [search, setSearch] = useState('');
  const list = useListPage<PlatformCastSummaryResponse, string>(
    (page, term) => platformCastApi.list({ page, size: 20, search: term || undefined }),
    ''
  );
  return (
    <ListPage
      title="キャスト在籍照会"
      description="本人を検索して、各店舗の在籍を確認します。"
      search={{
        onSearch: () => void list.search(search.trim()),
        content: (
          <>
            <Input
              aria-label="表示名または本名"
              placeholder="表示名または本名で検索"
              value={search}
              onChange={event => setSearch(event.target.value)}
              className="md:max-w-xs"
            />
            <Button type="submit">検索</Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setSearch('');
                void list.search('');
              }}
            >
              クリア
            </Button>
          </>
        ),
      }}
      state={list}
      emptyMessage="該当するキャストがいません"
      errorMessage="キャスト一覧を取得できません。閲覧権限を確認してください。"
      onRetry={list.reload}
    >
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>本人ID</TableHead>
            <TableHead>表示名</TableHead>
            <TableHead>本名</TableHead>
            <TableHead>操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {list.rows.map(person => (
            <TableRow key={person.id}>
              <TableCell>{person.id}</TableCell>
              <TableCell>{person.display_name}</TableCell>
              <TableCell>{person.real_name ?? '未登録'}</TableCell>
              <TableCell>
                <Button variant="ghost" render={<Link href={`/platform/casts/${person.id}`} />}>
                  在籍を確認
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </ListPage>
  );
}
