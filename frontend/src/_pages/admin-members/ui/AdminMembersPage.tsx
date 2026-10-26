'use client';

import { useState } from 'react';
import Link from 'next/link';
import {
  AdminMemberSummary,
  MEMBER_RANK_LABELS,
  MemberStatusFilter,
  adminMemberApi,
} from '@/entities/member';
import { useListPage } from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
import {
  Badge,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Button,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import { formatDateTime } from './format';

/** 一覧 1 ページあたりの件数 */
const PAGE_SIZE = 20;

const STATUS_OPTIONS: { value: MemberStatusFilter; label: string }[] = [
  { value: 'ACTIVE', label: '有効' },
  { value: 'WITHDRAWN', label: '退会済み' },
  { value: 'ALL', label: 'すべて' },
];

interface Criteria {
  status: MemberStatusFilter;
  search: string;
}

function StatusBadge({ member }: { member: AdminMemberSummary }) {
  return member.withdrawn_at ? (
    <Badge variant="outline" className="border-transparent bg-muted text-foreground">
      退会済み
    </Badge>
  ) : (
    <Badge variant="outline" className="border-transparent bg-success/10 text-success-strong">
      有効
    </Badge>
  );
}

/**
 * 会員の一覧（グループ級）。既定は有効な会員だけで、退会済みは状態を切り替えて見る。
 * 検索語は会員コード・電子メール・表示名のいずれにも当たる。
 */
export function AdminMembersPage() {
  // 入力中の値。適用済みの条件は list が持ち、検索ボタン（Enter）で確定する
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<MemberStatusFilter>('ACTIVE');
  const list = useListPage<AdminMemberSummary, Criteria>(
    (page, criteria) =>
      adminMemberApi.list({
        page,
        size: PAGE_SIZE,
        status: criteria.status,
        search: criteria.search || undefined,
      }),
    { status: 'ACTIVE', search: '' }
  );

  return (
    <ListPage
      title="会員"
      description="グループの会員を検索・照会します。退会済みの会員は状態を切り替えて表示します。"
      search={{
        onSearch: () => void list.search({ status, search }),
        content: (
          <>
            <Input
              placeholder="会員コード・メールアドレス・表示名で検索"
              aria-label="検索語"
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full md:max-w-sm"
            />
            <Select
              items={STATUS_OPTIONS}
              value={status}
              onValueChange={value => {
                if (!value) return;
                const next = value as MemberStatusFilter;
                setStatus(next);
                // 状態の切替は検索ボタンを待たずに適用する。入力中の検索語は読まず、適用済み条件の差分更新にする
                void list.search(previous => ({ ...previous, status: next }));
              }}
            >
              <SelectTrigger aria-label="状態" className="w-full md:w-40">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {STATUS_OPTIONS.map(option => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button type="submit" variant="outline">
              検索
            </Button>
          </>
        ),
      }}
      state={list}
      emptyMessage="該当する会員がいません"
      errorMessage="会員一覧の取得に失敗しました"
      onRetry={list.reload}
    >
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>会員コード</TableHead>
            <TableHead>表示名</TableHead>
            <TableHead>メールアドレス</TableHead>
            <TableHead>ランク</TableHead>
            <TableHead>状態</TableHead>
            <TableHead>登録日時</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {list.rows.map(member => (
            <TableRow key={member.id}>
              <TableCell className="font-mono">
                <Link
                  href={`/admin/members/${member.id}`}
                  className="text-primary-strong hover:underline"
                >
                  {member.member_code}
                </Link>
              </TableCell>
              <TableCell className="font-medium text-foreground">{member.display_name}</TableCell>
              <TableCell className="text-muted-foreground">{member.email}</TableCell>
              <TableCell className="text-muted-foreground">
                {member.rank ? MEMBER_RANK_LABELS[member.rank] : '—'}
              </TableCell>
              <TableCell>
                <StatusBadge member={member} />
              </TableCell>
              <TableCell className="text-muted-foreground">
                {formatDateTime(member.registered_at)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </ListPage>
  );
}
